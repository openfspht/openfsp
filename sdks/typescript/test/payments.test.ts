import { describe, expect, test } from "bun:test"
import { ApiError, Money, NO_RETRY, OpenFsp, PaymentNotFoundError, TransportError, type RetryPolicy } from "../src/index"
import { FakeClock, FakeTransport, jsonResponse, problemResponse, wirePayment } from "../src/testing"

const REQUEST = { reference: "INV-184", amount: Money.htg(1250), provider: "moncash" }

function openfsp(transport: FakeTransport, retryPolicy?: RetryPolicy) {
  const clock = new FakeClock()
  return { openfsp: new OpenFsp({ baseUrl: "https://server.example/openfsp", apiKey: "ofsp_test_key", retryPolicy }, { transport, clock }), clock }
}

describe("payments.create", () => {
  test("posts the request with the idempotency key and reads the payment", async () => {
    const transport = new FakeTransport(jsonResponse(201, wirePayment()))
    const payment = await openfsp(transport).openfsp.payments.create(REQUEST, { idempotencyKey: "key-1" })

    const sent = transport.requests[0]!
    expect(payment.id).toBe("pay_test")
    expect(sent.method).toBe("POST")
    expect(sent.url.href).toBe("https://server.example/openfsp/v1/payments")
    expect(sent.headers.get("authorization")).toBe("Bearer ofsp_test_key")
    expect(sent.headers.get("idempotency-key")).toBe("key-1")
    expect(JSON.parse(sent.body!)).toEqual({ reference: "INV-184", amount: { amount: 125000, currency: "HTG" }, provider: "moncash" })
  })

  test("resends with the same key, waiting at least Retry-After", async () => {
    const transport = new FakeTransport(problemResponse(503, "provider-unavailable", { retryable: true }, { "Retry-After": "2" }), jsonResponse(201, wirePayment()))
    const { openfsp: client, clock } = openfsp(transport)
    await client.payments.create(REQUEST, { idempotencyKey: "key-1" })
    expect(transport.requests.map((request) => request.headers.get("idempotency-key"))).toEqual(["key-1", "key-1"])
    expect(clock.sleeps).toEqual([2000])
  })

  test("resends rate-limited requests but no other client error", async () => {
    const limited = new FakeTransport(problemResponse(429, "rate-limited", { retryable: true }), jsonResponse(201, wirePayment()))
    await openfsp(limited).openfsp.payments.create(REQUEST)
    expect(limited.requests).toHaveLength(2)

    const reused = new FakeTransport(problemResponse(422, "idempotency-key-reused", { retryable: true }))
    const failure = await openfsp(reused).openfsp.payments.create(REQUEST).catch((error: unknown) => error)
    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as ApiError).code).toBe("idempotency-key-reused")
    expect(reused.requests).toHaveLength(1)
  })

  test("reads the payment back when the outcome stays unknown", async () => {
    const lost = new TransportError("socket hang up")
    const transport = new FakeTransport(lost, lost, lost, jsonResponse(200, { data: [wirePayment()] }))
    const payment = await openfsp(transport).openfsp.payments.create(REQUEST)
    expect(payment.id).toBe("pay_test")
    expect(transport.requests.at(-1)!.url.search).toBe("?reference=INV-184")
  })

  test("keeps the original failure when the payment found differs", async () => {
    const transport = new FakeTransport(new TransportError("reset"), jsonResponse(200, { data: [wirePayment({ amount: { amount: 5, currency: "HTG" } })] }))
    await expect(openfsp(transport, NO_RETRY).openfsp.payments.create(REQUEST)).rejects.toBeInstanceOf(TransportError)
  })

  test("does not look the payment up when nothing happened", async () => {
    const transport = new FakeTransport(problemResponse(422, "invalid-field", { errors: [{ field: "/amount", code: "too_large" }] }))
    const failure = (await openfsp(transport).openfsp.payments.create(REQUEST).catch((error: unknown) => error)) as ApiError
    expect(failure.fieldErrors).toEqual([{ field: "/amount", code: "too_large" }])
    expect(transport.requests).toHaveLength(1)
  })

  test("refuses an invalid idempotency key before sending", async () => {
    const transport = new FakeTransport()
    await expect(openfsp(transport).openfsp.payments.create(REQUEST, { idempotencyKey: "not valid" })).rejects.toThrow("idempotencyKey")
    expect(transport.requests).toHaveLength(0)
  })
})

describe("payments.get, synchronize and findByReference", () => {
  test("read a payment", async () => {
    const transport = new FakeTransport(jsonResponse(200, wirePayment()), jsonResponse(200, wirePayment({ status: "succeeded" })))
    const { openfsp: client } = openfsp(transport)
    expect((await client.payments.get("pay_test")).isPending).toBe(true)
    expect((await client.payments.synchronize("pay_test")).isSuccessful).toBe(true)
    expect(transport.requests.map((request) => `${request.method} ${request.url.pathname}`)).toEqual(["GET /openfsp/v1/payments/pay_test", "POST /openfsp/v1/payments/pay_test/synchronize"])
  })

  test("report an unknown payment", async () => {
    const transport = new FakeTransport(problemResponse(404, "not-found"), jsonResponse(200, { data: [] }))
    const { openfsp: client } = openfsp(transport)
    await expect(client.payments.get("pay_missing")).rejects.toBeInstanceOf(PaymentNotFoundError)
    expect(await client.payments.findByReference("INV-404")).toBeUndefined()
  })

  test("refuse an identifier that would change the path", async () => {
    await expect(openfsp(new FakeTransport()).openfsp.payments.get("../admin")).rejects.toThrow("payment id")
  })

  test("treat a malformed or redirected answer as unexpected", async () => {
    const transport = new FakeTransport(jsonResponse(200, { id: "pay_test" }), { status: 302, headers: new Headers({ Location: "https://elsewhere.example" }), body: "" })
    const { openfsp: client } = openfsp(transport, NO_RETRY)
    expect(((await client.payments.get("pay_test").catch((error: unknown) => error)) as ApiError).code).toBe("unexpected-response")
    expect(((await client.payments.get("pay_test").catch((error: unknown) => error)) as ApiError).status).toBe(302)
  })
})

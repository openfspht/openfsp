import { describe, expect, test } from "bun:test"
import type { WebhookEvent } from "@workspace/openfsp-client"
import type { Payment, PaymentStatus } from "@workspace/types"

import { catalog } from "../domain/catalog"
import {
  InMemoryOrders,
  InMemoryProcessedEvents,
} from "../infrastructure/adapters"
import { Checkout, InvalidOrder } from "./checkout"
import type { Authenticity, PaymentGateway, WebhookAuthenticity } from "./ports"
import { WebhookReceiver } from "./webhook-receiver"

const clock = { now: () => new Date("2026-11-09T15:00:00Z") }

function payment(status: PaymentStatus = "pending"): Payment {
  return {
    id: "pay_1",
    reference: "SHOP-1",
    status,
    amount: { amount: 125000, currency: "HTG" },
    provider: "mock_alpha",
    provider_order_id: "order",
    created_at: "2026-11-09T15:00:00Z",
    updated_at: "2026-11-09T15:00:00Z",
    next_action: { type: "payer_approval" },
  }
}

const gateway: PaymentGateway = {
  createPayment: async () => payment(),
  getPayment: async () => payment("succeeded"),
  providers: async () => [
    {
      id: "mock_alpha",
      display_name: "Alpha (mock)",
      logo_url: null,
      capabilities: ["payments"],
    },
    {
      id: "mock_beta",
      display_name: "Beta (mock)",
      logo_url: null,
      capabilities: ["payments"],
    },
  ],
}

function event(
  sequence: number,
  status: PaymentStatus,
  reference: string,
  id = `evt_${sequence}`
): WebhookEvent {
  return {
    id,
    type:
      status === "pending"
        ? "payment.created"
        : (`payment.${status}` as WebhookEvent["type"]),
    created_at: "2026-11-09T15:00:00Z",
    sequence,
    resource_type: "payment",
    resource_id: "pay_1",
    reference,
    data: { ...payment(status), reference },
  }
}

class ScriptedAuthenticity implements WebhookAuthenticity {
  next: Authenticity = { verified: false, reason: "unsigned" }

  async verify(): Promise<Authenticity> {
    return this.next
  }
}

function setUp(paymentGateway: PaymentGateway = gateway) {
  const orders = new InMemoryOrders()
  const checkout = new Checkout(catalog, paymentGateway, orders, clock, {
    providers: ["mock_alpha"],
    publicUrl: "https://shop.example",
  })
  const authenticity = new ScriptedAuthenticity()
  const receiver = new WebhookReceiver(
    authenticity,
    orders,
    new InMemoryProcessedEvents(),
    paymentGateway,
    clock
  )
  const deliver = async (verified: WebhookEvent) => {
    authenticity.next = { verified: true, event: verified }
    return receiver.receive({
      method: "POST",
      headers: new Headers(),
      body: new Uint8Array(0),
    })
  }
  return { orders, checkout, authenticity, receiver, deliver }
}

describe("checkout", () => {
  test("offers the providers of the gateway, narrowed by the configuration", async () => {
    const { checkout } = setUp()
    expect(await checkout.providers()).toEqual([
      { id: "mock_alpha", name: "Alpha (mock)", logo_url: null },
    ])
    const open = new Checkout(catalog, gateway, new InMemoryOrders(), clock, {
      publicUrl: "https://shop.example",
    })
    expect((await open.providers()).map((provider) => provider.id)).toEqual([
      "mock_alpha",
      "mock_beta",
    ])
  })

  test("places an order for a known product and provider", async () => {
    const { checkout } = setUp()
    const order = await checkout.place(
      [
        { product_id: "coffee", quantity: 2 },
        { product_id: "mangoes", quantity: 1 },
      ],
      "+50934567890",
      "mock_alpha"
    )
    expect(order.status).toBe("pending")
    expect(order.amount).toEqual({ amount: 82_500, currency: "HTG" })
    expect(order.lines.map((line) => line.quantity)).toEqual([2, 1])
    expect(checkout.list()).toHaveLength(1)
  })

  test("sends an eight-digit phone number to the gateway in E.164", async () => {
    let sent: string | undefined
    const recording: PaymentGateway = {
      ...gateway,
      createPayment: async (request) => {
        sent = request.payer?.phone_number
        return payment()
      },
    }
    const checkout = new Checkout(
      catalog,
      recording,
      new InMemoryOrders(),
      clock,
      { publicUrl: "https://shop.example" }
    )
    await checkout.place(
      [{ product_id: "coffee", quantity: 1 }],
      "3456 7890",
      "mock_alpha"
    )
    expect(sent).toBe("+50934567890")
  })

  test("refuses an empty cart or a quantity out of range", async () => {
    const { checkout } = setUp()
    expect(checkout.place([], "", "mock_alpha")).rejects.toBeInstanceOf(
      InvalidOrder
    )
    expect(
      checkout.place([{ product_id: "coffee", quantity: 21 }], "", "mock_alpha")
    ).rejects.toBeInstanceOf(InvalidOrder)
  })

  test("refuses an unknown provider", async () => {
    const { checkout } = setUp()
    expect(
      checkout.place(
        [{ product_id: "coffee", quantity: 1 }],
        "+50934567890",
        "moncash"
      )
    ).rejects.toBeInstanceOf(InvalidOrder)
  })

  test("reads the order status from the gateway on return", async () => {
    const { checkout } = setUp()
    const order = await checkout.place(
      [
        { product_id: "coffee", quantity: 2 },
        { product_id: "mangoes", quantity: 1 },
      ],
      "+50934567890",
      "mock_alpha"
    )
    expect((await checkout.read(order.reference)).status).toBe("succeeded")
  })
})

describe("webhook receiver", () => {
  test("rejects an unverified delivery", async () => {
    const { receiver } = setUp()
    expect(
      await receiver.receive({
        method: "POST",
        headers: new Headers(),
        body: new Uint8Array(0),
      })
    ).toEqual({ accepted: false, reason: "unsigned" })
  })

  test("applies events in sequence, ignores duplicates and stale ones, and never leaves a terminal state", async () => {
    const { checkout, deliver, orders } = setUp()
    const { reference } = await checkout.place(
      [{ product_id: "coffee", quantity: 1 }],
      "+50934567890",
      "mock_alpha"
    )
    expect(
      (await deliver(event(2, "succeeded", reference))).accepted &&
        orders.find(reference)?.status
    ).toBe("succeeded")
    expect(await deliver(event(2, "succeeded", reference))).toMatchObject({
      outcome: "duplicate",
    })
    expect(await deliver(event(1, "pending", reference))).toMatchObject({
      outcome: "stale",
    })
    expect(await deliver(event(3, "failed", reference))).toMatchObject({
      outcome: "terminal",
    })
    expect(orders.find(reference)?.status).toBe("succeeded")
  })

  test("acknowledges an event of an unknown type without applying it", async () => {
    const { checkout, deliver, orders } = setUp()
    const { reference } = await checkout.place(
      [{ product_id: "coffee", quantity: 1 }],
      "+50934567890",
      "mock_alpha"
    )
    const unknown = {
      ...event(1, "failed", reference),
      type: "payment.refunded",
    } as unknown as WebhookEvent
    expect(await deliver(unknown)).toMatchObject({
      accepted: true,
      outcome: "ignored",
    })
    expect(orders.find(reference)?.status).toBe("pending")
  })

  test("re-reads the payment from the gateway instead of leaving a terminal state", async () => {
    const reads: string[] = []
    const { checkout, deliver, orders } = setUp({
      ...gateway,
      getPayment: async (id) => {
        reads.push(id)
        return payment("succeeded")
      },
    })
    const { reference } = await checkout.place(
      [{ product_id: "coffee", quantity: 1 }],
      "+50934567890",
      "mock_alpha"
    )
    await deliver(event(1, "pending", reference))
    await deliver(event(2, "succeeded", reference))
    expect(reads).toEqual([])
    expect(await deliver(event(3, "failed", reference))).toMatchObject({
      outcome: "terminal",
    })
    expect(reads).toEqual(["pay_1"])
    expect(orders.find(reference)?.status).toBe("succeeded")
  })

  test("re-reads the payment from the gateway when the sequence skips ahead", async () => {
    const reads: string[] = []
    const { checkout, deliver, orders } = setUp({
      ...gateway,
      getPayment: async (id) => {
        reads.push(id)
        return payment("expired")
      },
    })
    const { reference } = await checkout.place(
      [{ product_id: "coffee", quantity: 1 }],
      "+50934567890",
      "mock_alpha"
    )
    await deliver(event(1, "pending", reference))
    expect(await deliver(event(3, "failed", reference))).toMatchObject({
      outcome: "gap",
    })
    expect(reads).toEqual(["pay_1"])
    expect(orders.find(reference)?.status).toBe("expired")
    expect(await deliver(event(2, "succeeded", reference))).toMatchObject({
      outcome: "stale",
    })
  })
})

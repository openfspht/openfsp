import { describe, expect, test } from "bun:test"
import { ApiError, Money, newIdempotencyKey, OpenFsp } from "../src/index"

describe.skipIf(process.env.OPENFSP_INTEGRATION !== "1")("against the gateway", () => {
    test("creates, reads and synchronizes a payment through the gateway", async () => {
        const openfsp = new OpenFsp({
            baseUrl: process.env.OPENFSP_BASE_URL ?? "http://127.0.0.1:8080",
            apiKey: process.env.OPENFSP_API_KEY ?? "",
        })
        const provider = (await openfsp.providers()).find(
            (candidate) => !candidate.returnUrlRequired && !candidate.payerRequired
        )
        const request = {
            reference: `SDK-TS-${Date.now()}`,
            amount: Money.htg(1250),
            provider: provider!.id,
            description: "SDK interop",
        }
        const idempotencyKey = newIdempotencyKey()

        const created = await openfsp.payments.create(request, { idempotencyKey })
        const replayed = await openfsp.payments.create(request, { idempotencyKey })

        expect(replayed.id).toBe(created.id)
        expect(created.amount.equals(request.amount)).toBe(true)
        expect((await openfsp.payments.get(created.id)).id).toBe(created.id)
        expect((await openfsp.payments.findByReference(request.reference))?.id).toBe(created.id)
        expect((await openfsp.payments.synchronize(created.id)).id).toBe(created.id)
        expect((await openfsp.configuration()).webhookKeys.length).toBeGreaterThan(0)
        const reused = await openfsp.payments
            .create({ ...request, amount: Money.htg(1300) }, { idempotencyKey })
            .catch((error: unknown) => error)
        expect((reused as ApiError).code).toBe("idempotency-key-reused")
    })
})

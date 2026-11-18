import { describe, expect, test } from "bun:test"
import { Webhooks, type WebhookDelivery, type WebhookKey } from "../src/index"

interface Step {
    now: number
    valid: boolean
    delivery: WebhookDelivery
}

const vectors: { keys: WebhookKey[]; cases: { name: string; steps: Step[] }[] } = await Bun.file(
    new URL("../../../conformance/vectors/webhooks.json", import.meta.url)
).json()

describe("shared webhook vectors", () => {
    test.each(vectors.cases.map((vector) => [vector.name, vector.steps] as const))(
        "%s",
        async (_, steps) => {
            let now = 0
            const webhooks = Webhooks.withKeys(vectors.keys, { now: () => now })
            for (const step of steps) {
                now = step.now
                const outcome = await webhooks.verify(step.delivery).then(
                    () => true,
                    () => false
                )
                expect(outcome).toBe(step.valid)
            }
        }
    )
})

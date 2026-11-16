import { describe, expect, test } from "bun:test"
import { Webhooks, type WebhookDelivery, type WebhookKey } from "../src/index"

interface Vector {
  name: string
  now: number
  valid: boolean
  delivery: WebhookDelivery
}

const vectors: { key: WebhookKey; cases: Vector[] } = await Bun.file(new URL("../../../conformance/vectors/webhooks.json", import.meta.url)).json()

describe("shared webhook vectors", () => {
  test.each(vectors.cases.map((vector) => [vector.name, vector] as const))("%s", async (_, vector) => {
    const webhooks = Webhooks.withKeys([vectors.key], { now: () => vector.now })
    const outcome = await webhooks.verify(vector.delivery).then(() => true, () => false)
    expect(outcome).toBe(vector.valid)
  })
})

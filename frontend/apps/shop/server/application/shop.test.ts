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

function setUp() {
  const orders = new InMemoryOrders()
  const checkout = new Checkout(catalog, gateway, orders, clock, {
    providers: ["mock_alpha"],
    publicUrl: "https://shop.example",
  })
  const authenticity = new ScriptedAuthenticity()
  const receiver = new WebhookReceiver(
    authenticity,
    orders,
    new InMemoryProcessedEvents(),
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
  test("places an order for a known product and provider", async () => {
    const { checkout } = setUp()
    const order = await checkout.place("coffee", "+50934567890", "mock_alpha")
    expect(order.status).toBe("pending")
    expect(checkout.list()).toHaveLength(1)
  })

  test("refuses an unknown provider", async () => {
    const { checkout } = setUp()
    expect(
      checkout.place("coffee", "+50934567890", "moncash")
    ).rejects.toBeInstanceOf(InvalidOrder)
  })

  test("reads the order status from the gateway on return", async () => {
    const { checkout } = setUp()
    const order = await checkout.place("coffee", "+50934567890", "mock_alpha")
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
      "coffee",
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
})

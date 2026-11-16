import { afterAll, describe, expect, test } from "bun:test"
import { Money, Payment } from "@openfsp/sdk"

import { Checkout } from "../application/checkout"
import type { PaymentGateway, WebhookAuthenticity } from "../application/ports"
import { WebhookReceiver } from "../application/webhook-receiver"
import { catalog } from "../domain/catalog"
import {
  InMemoryOrders,
  InMemoryProcessedEvents,
} from "../infrastructure/adapters"
import { routes } from "./routes"

const clock = { now: () => new Date("2026-11-09T15:00:00Z") }

const gateway: PaymentGateway = {
  createPayment: async (request) =>
    new Payment({
      id: `pay_${request.reference}`,
      reference: request.reference,
      status: "pending",
      amount: Money.htg(1250),
      provider: "mock_alpha",
      providerOrderId: "order",
      nextAction: { type: "payer_approval" },
      createdAt: clock.now(),
      updatedAt: clock.now(),
      metadata: {},
    }),
  getPayment: async () => {
    throw new Error("not used")
  },
  findPayment: async () => undefined,
  providers: async () => [{ id: "mock_alpha", displayName: "Alpha (mock)" }],
}

const unsigned: WebhookAuthenticity = {
  verify: async () => ({ verified: false, reason: "unsigned" }),
}

const orders = new InMemoryOrders()
const server = Bun.serve({
  port: 0,
  hostname: "127.0.0.1",
  routes: routes(
    new Checkout(catalog, gateway, orders, clock, {
      publicUrl: "https://shop.example",
    }),
    new WebhookReceiver(
      unsigned,
      orders,
      new InMemoryProcessedEvents(),
      gateway,
      clock
    )
  ),
})
afterAll(() => server.stop(true))

const place = (cookie?: string) =>
  fetch(new URL("/api/orders", server.url), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: JSON.stringify({
      lines: [{ product_id: "coffee", quantity: 1 }],
      provider: "mock_alpha",
    }),
  })

const listed = async (cookie?: string) => {
  const response = await fetch(new URL("/api/orders", server.url), {
    headers: cookie ? { Cookie: cookie } : {},
  })
  return (
    (await response.json()) as { data: { reference: string }[] }
  ).data.map((order) => order.reference)
}

describe("orders", () => {
  test("lists only the orders placed by the same browser", async () => {
    const first = await place()
    const cookie = first.headers.get("Set-Cookie")?.split(";")[0]
    expect(first.headers.get("Set-Cookie")).toContain("HttpOnly")
    const own = ((await first.json()) as { reference: string }).reference
    const again = await place(cookie)
    expect(again.headers.get("Set-Cookie")).toBeNull()
    const second = ((await again.json()) as { reference: string }).reference
    await place()

    expect((await listed(cookie)).sort()).toEqual([own, second].sort())
    expect(await listed()).toEqual([])
    expect(await listed("shop_session=guessed")).toEqual([])
  })
})

import { OpenFSPError } from "@workspace/openfsp-client"

import { Checkout, InvalidOrder, UnknownOrder } from "../application/checkout"
import type { WebhookReceiver } from "../application/webhook-receiver"

type Route = (request: Bun.BunRequest) => Promise<Response> | Response

export function routes(
  checkout: Checkout,
  receiver: WebhookReceiver
): Record<string, Partial<Record<"GET" | "POST", Route>>> {
  return {
    "/api/products": {
      GET: () =>
        Response.json({
          data: checkout.products(),
          providers: checkout.providers(),
        }),
    },
    "/api/orders": {
      GET: () => Response.json({ data: checkout.list() }),
      POST: (request) =>
        handle(async () => {
          const body = (await request.json()) as {
            product_id?: string
            phone_number?: string
            provider?: string
          }
          return Response.json(
            await checkout.place(
              body.product_id ?? "",
              body.phone_number ?? "",
              body.provider ?? ""
            ),
            { status: 201 }
          )
        }),
    },
    "/api/orders/:reference": {
      GET: (request) =>
        handle(async () =>
          Response.json(
            await checkout.read(
              (request.params as { reference: string }).reference
            )
          )
        ),
    },
    "/webhooks/openfsp": {
      POST: async (request) => {
        const reception = await receiver.receive({
          method: request.method,
          headers: request.headers,
          body: new Uint8Array(await request.arrayBuffer()),
        })
        if (!reception.accepted) {
          console.warn(`Rejected a webhook: ${reception.reason}`)
          return new Response(null, { status: 400 })
        }
        console.info(`${reception.summary}: ${reception.outcome}`)
        return new Response(null, { status: 204 })
      },
    },
  }
}

async function handle(work: () => Promise<Response>): Promise<Response> {
  try {
    return await work()
  } catch (error) {
    if (error instanceof InvalidOrder)
      return Response.json({ error: error.message }, { status: 400 })
    if (error instanceof UnknownOrder)
      return Response.json({ error: "unknown order" }, { status: 404 })
    if (error instanceof OpenFSPError)
      return Response.json(error.problem, { status: error.problem.status })
    throw error
  }
}

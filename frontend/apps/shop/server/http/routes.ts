import { OpenFSPError } from "@workspace/openfsp-client"

import { Checkout, InvalidOrder, UnknownOrder } from "../application/checkout"
import type { WebhookReceiver } from "../application/webhook-receiver"

const PAGE_SIZE = 12
const MAX_PAGE_SIZE = 100

type Route = (request: Bun.BunRequest) => Promise<Response> | Response

export function routes(
  checkout: Checkout,
  receiver: WebhookReceiver
): Record<string, Partial<Record<"GET" | "POST", Route>>> {
  return {
    "/api/products": {
      GET: async () =>
        Response.json({
          data: checkout.products(),
          providers: await checkout.providers(),
        }),
    },
    "/api/orders": {
      GET: (request) => {
        const query = new URL(request.url).searchParams
        const size = Math.min(
          Math.max(Number(query.get("size")) || PAGE_SIZE, 1),
          MAX_PAGE_SIZE
        )
        const page = Math.max(Number(query.get("page")) || 0, 0)
        const all = checkout.list()
        return Response.json({
          data: all.slice(page * size, page * size + size),
          page: {
            number: page,
            size,
            total_elements: all.length,
            total_pages: Math.ceil(all.length / size),
          },
        })
      },
      POST: (request) =>
        handle(async () => {
          const body = (await request.json()) as {
            lines?: { product_id: string; quantity: number }[]
            phone_number?: string
            provider?: string
          }
          return Response.json(
            await checkout.place(
              Array.isArray(body.lines) ? body.lines : [],
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
        if (reception.outcome === "terminal")
          console.warn(
            `${reception.summary} would leave a terminal state, re-read the payment from the gateway`
          )
        else console.info(`${reception.summary}: ${reception.outcome}`)
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

import { OpenFSPClient, OpenFSPError, WebhookVerifier } from "@workspace/openfsp-client"
import { existsSync } from "node:fs"
import { join } from "node:path"

import { catalog } from "./catalog"
import { applyEvent, findOrder, listOrders, saveOrder } from "./orders"

const gatewayUrl = process.env.OPENFSP_GATEWAY_URL ?? "http://127.0.0.1:8080"
const apiKey = process.env.OPENFSP_API_KEY
const webhookUrl = process.env.SHOP_WEBHOOK_URL ?? "http://127.0.0.1:3000/webhooks/openfsp"
const providers = (process.env.OPENFSP_PROVIDERS ?? "mock_alpha,mock_epsilon").split(",")
const publicUrl = process.env.SHOP_PUBLIC_URL ?? "http://localhost:5174"
const port = Number(process.env.PORT ?? 3000)
const dist = join(import.meta.dir, "..", "dist")

if (!apiKey) throw new Error("OPENFSP_API_KEY is required: use the key the gateway prints on its first start")

const gateway = new OpenFSPClient(gatewayUrl, apiKey)
const verifier = new WebhookVerifier(gatewayUrl)

async function createOrder(request: Request): Promise<Response> {
  const { product_id, phone_number, provider } = (await request.json()) as { product_id?: string; phone_number?: string; provider?: string }
  const product = catalog.find((candidate) => candidate.id === product_id)
  if (!product || !phone_number || !provider || !providers.includes(provider)) {
    return Response.json({ error: "product_id, phone_number and a known provider are required" }, { status: 400 })
  }

  const reference = `SHOP-${Date.now().toString(36).toUpperCase()}`
  try {
    const payment = await gateway.createPayment({
      reference,
      amount: product.price,
      provider,
      payer: { phone_number },
      description: product.name,
      return_url: `${publicUrl}/orders/${reference}`,
      metadata: { product: product.id },
    })
    const order = {
      reference,
      product: product.name,
      amount: product.price,
      payment_id: payment.id,
      provider,
      status: payment.status,
      next_action: payment.next_action,
      created_at: payment.created_at,
      events: [],
    }
    saveOrder(order)
    return Response.json(order, { status: 201 })
  } catch (error) {
    if (error instanceof OpenFSPError) return Response.json(error.problem, { status: error.problem.status })
    throw error
  }
}

async function receiveWebhook(request: Request): Promise<Response> {
  const body = new Uint8Array(await request.arrayBuffer())
  const result = await verifier.verify({ method: request.method, targetUri: webhookUrl, headers: request.headers, body })
  if (!result.verified) {
    console.warn(`Rejected a webhook: ${result.reason}`)
    return new Response(null, { status: 400 })
  }
  const outcome = applyEvent(result.event)
  console.info(`${result.event.type} #${result.event.sequence} for ${result.event.reference}: ${outcome}`)
  return new Response(null, { status: 204 })
}

async function readOrder(reference: string): Promise<Response> {
  const order = findOrder(reference)
  if (!order) return Response.json({ error: "unknown order" }, { status: 404 })
  const payment = await gateway.getPayment(order.payment_id)
  order.status = payment.status
  order.next_action = payment.next_action
  return Response.json(order)
}

function staticFile(pathname: string): Response {
  const file = Bun.file(join(dist, pathname === "/" ? "index.html" : pathname))
  return new Response(file.size > 0 ? file : Bun.file(join(dist, "index.html")))
}

Bun.serve({
  port,
  hostname: process.env.HOST ?? "127.0.0.1",
  routes: {
    "/api/products": { GET: () => Response.json({ data: catalog, providers }) },
    "/api/orders/:reference": { GET: (request) => readOrder(request.params.reference) },
    "/api/orders": { GET: () => Response.json({ data: listOrders() }), POST: createOrder },
    "/webhooks/openfsp": { POST: receiveWebhook },
  },
  fetch: (request) => (existsSync(dist) ? staticFile(new URL(request.url).pathname) : new Response("Not found", { status: 404 })),
})

console.info(`Demo shop on http://127.0.0.1:${port}, payments through ${gatewayUrl} with ${providers.join(", ")}`)

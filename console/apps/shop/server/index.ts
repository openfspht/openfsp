import { OpenFSPClient, OpenFSPError, WebhookVerifier } from "@workspace/openfsp-client"
import { existsSync } from "node:fs"
import { join } from "node:path"

import { catalog } from "./catalog"
import { applyEvent, listOrders, saveOrder } from "./orders"

const gatewayUrl = process.env.OPENFSP_GATEWAY_URL ?? "http://127.0.0.1:8080"
const apiKey = process.env.OPENFSP_API_KEY
const webhookUrl = process.env.SHOP_WEBHOOK_URL ?? "http://127.0.0.1:3000/webhooks/openfsp"
const provider = process.env.OPENFSP_PROVIDER ?? "mock_alpha"
const port = Number(process.env.PORT ?? 3000)
const dist = join(import.meta.dir, "..", "dist")

if (!apiKey) throw new Error("OPENFSP_API_KEY is required: use the key the gateway prints on its first start")

const gateway = new OpenFSPClient(gatewayUrl, apiKey)
const verifier = new WebhookVerifier(gatewayUrl)

async function createOrder(request: Request): Promise<Response> {
  const { product_id, phone_number } = (await request.json()) as { product_id?: string; phone_number?: string }
  const product = catalog.find((candidate) => candidate.id === product_id)
  if (!product || !phone_number) return Response.json({ error: "product_id and phone_number are required" }, { status: 400 })

  const reference = `SHOP-${Date.now().toString(36).toUpperCase()}`
  try {
    const payment = await gateway.createPayment({
      reference,
      amount: product.price,
      provider,
      payer: { phone_number },
      description: product.name,
      metadata: { product: product.id },
    })
    const order = {
      reference,
      product: product.name,
      amount: product.price,
      payment_id: payment.id,
      status: payment.status,
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

function staticFile(pathname: string): Response {
  const file = Bun.file(join(dist, pathname === "/" ? "index.html" : pathname))
  return new Response(file.size > 0 ? file : Bun.file(join(dist, "index.html")))
}

Bun.serve({
  port,
  hostname: "127.0.0.1",
  routes: {
    "/api/products": { GET: () => Response.json({ data: catalog }) },
    "/api/orders": { GET: () => Response.json({ data: listOrders() }), POST: createOrder },
    "/webhooks/openfsp": { POST: receiveWebhook },
  },
  fetch: (request) => (existsSync(dist) ? staticFile(new URL(request.url).pathname) : new Response("Not found", { status: 404 })),
})

console.info(`Demo shop on http://127.0.0.1:${port}, payments through ${gatewayUrl} with ${provider}`)

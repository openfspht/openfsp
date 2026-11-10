import { OpenFSPClient, WebhookVerifier } from "@workspace/openfsp-client"
import { existsSync } from "node:fs"
import { join } from "node:path"

import { Checkout } from "./application/checkout"
import { WebhookReceiver } from "./application/webhook-receiver"
import { readConfig } from "./config"
import { catalog } from "./domain/catalog"
import { routes } from "./http/routes"
import {
  InMemoryOrders,
  InMemoryProcessedEvents,
  OpenFSPPaymentGateway,
  SignedWebhooks,
  systemClock,
} from "./infrastructure/adapters"

const config = readConfig(process.env)
const orders = new InMemoryOrders()
const checkout = new Checkout(
  catalog,
  new OpenFSPPaymentGateway(
    new OpenFSPClient(config.gatewayUrl, config.apiKey)
  ),
  orders,
  systemClock,
  {
    providers: config.providers,
    publicUrl: config.publicUrl,
  }
)
const receiver = new WebhookReceiver(
  new SignedWebhooks(new WebhookVerifier(config.gatewayUrl), config.webhookUrl),
  orders,
  new InMemoryProcessedEvents(),
  systemClock
)
const dist = join(import.meta.dir, "..", "dist")

Bun.serve({
  port: config.port,
  hostname: config.host,
  routes: routes(checkout, receiver),
  fetch: (request) => {
    if (!existsSync(dist)) return new Response("Not found", { status: 404 })
    const path = new URL(request.url).pathname
    const file = Bun.file(join(dist, path === "/" ? "index.html" : path))
    return new Response(
      file.size > 0 ? file : Bun.file(join(dist, "index.html"))
    )
  },
})

console.info(
  `Demo shop on http://${config.host}:${config.port}, payments through ${config.gatewayUrl} with ${config.providers.join(", ")}`
)

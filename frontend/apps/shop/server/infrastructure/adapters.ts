import { WebhookVerifier, type OpenFSPClient } from "@workspace/openfsp-client"

import type { Order } from "../domain/order"
import type {
  Authenticity,
  Clock,
  Orders,
  PaymentGateway,
  ProcessedEvents,
  WebhookAuthenticity,
} from "../application/ports"

export class OpenFSPPaymentGateway implements PaymentGateway {
  private readonly client: OpenFSPClient

  constructor(client: OpenFSPClient) {
    this.client = client
  }

  createPayment = (request: Parameters<OpenFSPClient["createPayment"]>[0]) =>
    this.client.createPayment(request)

  getPayment = (id: string) => this.client.getPayment(id)

  providers = () => this.client.providers()
}

export class SignedWebhooks implements WebhookAuthenticity {
  private readonly verifier: WebhookVerifier
  private readonly targetUri: string

  constructor(verifier: WebhookVerifier, targetUri: string) {
    this.verifier = verifier
    this.targetUri = targetUri
  }

  verify(request: {
    method: string
    headers: Headers
    body: Uint8Array<ArrayBuffer>
  }): Promise<Authenticity> {
    return this.verifier.verify({ ...request, targetUri: this.targetUri })
  }
}

export class InMemoryOrders implements Orders {
  private readonly orders = new Map<string, Order>()

  save(order: Order) {
    this.orders.set(order.reference, order)
  }

  find(reference: string) {
    return this.orders.get(reference)
  }

  all() {
    return [...this.orders.values()]
  }
}

export class InMemoryProcessedEvents implements ProcessedEvents {
  private readonly seen = new Set<string>()

  remember(eventId: string): boolean {
    if (this.seen.has(eventId)) return false
    this.seen.add(eventId)
    return true
  }
}

export const systemClock: Clock = { now: () => new Date() }

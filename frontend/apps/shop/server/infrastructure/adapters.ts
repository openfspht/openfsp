import { InvalidSignatureError, type OpenFsp, type PaymentRequest } from "@openfsp/sdk"

import type { Order } from "../domain/order"
import type {
  Authenticity,
  Clock,
  Orders,
  PaymentGateway,
  ProcessedEvents,
  WebhookAuthenticity,
} from "../application/ports"

export class OpenFspPaymentGateway implements PaymentGateway {
  private readonly openfsp: OpenFsp

  constructor(openfsp: OpenFsp) {
    this.openfsp = openfsp
  }

  createPayment = (request: PaymentRequest, idempotencyKey: string) =>
    this.openfsp.payments.create(request, { idempotencyKey })

  getPayment = (id: string) => this.openfsp.payments.get(id)

  findPayment = (reference: string) =>
    this.openfsp.payments.findByReference(reference)

  providers = () => this.openfsp.providers()
}

export class SignedWebhooks implements WebhookAuthenticity {
  private readonly openfsp: OpenFsp

  constructor(openfsp: OpenFsp) {
    this.openfsp = openfsp
  }

  async verify(request: Request): Promise<Authenticity> {
    try {
      return { verified: true, event: await this.openfsp.webhooks.verify(request) }
    } catch (failure) {
      if (failure instanceof InvalidSignatureError)
        return { verified: false, reason: failure.message }
      throw failure
    }
  }
}

export class InMemoryOrders implements Orders {
  private readonly orders = new Map<string, Order>()

  save(order: Order) {
    this.orders.set(order.reference, order)
  }

  remove(reference: string) {
    this.orders.delete(reference)
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

  has(eventId: string): boolean {
    return this.seen.has(eventId)
  }

  remember(eventId: string) {
    this.seen.add(eventId)
  }
}

export const systemClock: Clock = { now: () => new Date() }

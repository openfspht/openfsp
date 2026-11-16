import {
  applyPaymentEvent,
  isKnownEventType,
  nextActionOf,
  RESYNC_OUTCOMES,
  syncWithPayment,
  type EventOutcome,
} from "../domain/order"
import type {
  Clock,
  Orders,
  PaymentGateway,
  ProcessedEvents,
  WebhookAuthenticity,
} from "./ports"

export type Reception =
  | { accepted: false; reason: string }
  | {
      accepted: true
      outcome: EventOutcome | "duplicate" | "unknown-order" | "ignored"
      summary: string
    }

export class WebhookReceiver {
  private readonly authenticity: WebhookAuthenticity
  private readonly orders: Orders
  private readonly processed: ProcessedEvents
  private readonly gateway: PaymentGateway
  private readonly clock: Clock

  constructor(
    authenticity: WebhookAuthenticity,
    orders: Orders,
    processed: ProcessedEvents,
    gateway: PaymentGateway,
    clock: Clock
  ) {
    this.authenticity = authenticity
    this.orders = orders
    this.processed = processed
    this.gateway = gateway
    this.clock = clock
  }

  async receive(request: Request): Promise<Reception> {
    const result = await this.authenticity.verify(request)
    if (!result.verified) return { accepted: false, reason: result.reason }
    const event = result.event
    const summary = `${event.type} #${event.sequence} for ${event.reference}`
    if (!isKnownEventType(event.type))
      return { accepted: true, outcome: "ignored", summary }
    if (this.processed.has(event.id))
      return { accepted: true, outcome: "duplicate", summary }
    const found = this.orders.find(event.reference)
    if (!found) return { accepted: true, outcome: "unknown-order", summary }
    const order = { ...found, payment_id: found.payment_id ?? event.payment.id }
    const { order: applied, outcome } = applyPaymentEvent(
      order,
      {
        id: event.id,
        type: event.type,
        sequence: event.sequence,
        status: event.payment.status,
        nextAction: nextActionOf(event.payment),
      },
      this.clock.now().toISOString()
    )
    const updated = RESYNC_OUTCOMES.has(outcome)
      ? syncWithPayment(
          applied,
          await this.gateway.getPayment(order.payment_id)
        )
      : applied
    this.orders.save(updated)
    this.processed.remember(event.id)
    return { accepted: true, outcome, summary }
  }
}

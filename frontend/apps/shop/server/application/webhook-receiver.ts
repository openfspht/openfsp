import {
  applyPaymentEvent,
  isKnownEventType,
  type EventOutcome,
} from "../domain/order"
import type {
  Clock,
  Orders,
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
  private readonly clock: Clock

  constructor(
    authenticity: WebhookAuthenticity,
    orders: Orders,
    processed: ProcessedEvents,
    clock: Clock
  ) {
    this.authenticity = authenticity
    this.orders = orders
    this.processed = processed
    this.clock = clock
  }

  async receive(request: {
    method: string
    headers: Headers
    body: Uint8Array<ArrayBuffer>
  }): Promise<Reception> {
    const result = await this.authenticity.verify(request)
    if (!result.verified) return { accepted: false, reason: result.reason }
    const event = result.event
    const summary = `${event.type} #${event.sequence} for ${event.reference}`
    if (!isKnownEventType(event.type))
      return { accepted: true, outcome: "ignored", summary }
    if (!this.processed.remember(event.id))
      return { accepted: true, outcome: "duplicate", summary }
    const order = this.orders.find(event.reference)
    if (!order) return { accepted: true, outcome: "unknown-order", summary }
    const { order: updated, outcome } = applyPaymentEvent(
      order,
      {
        id: event.id,
        type: event.type,
        sequence: event.sequence,
        status: event.data.status,
        nextAction: event.data.next_action,
      },
      this.clock.now().toISOString()
    )
    this.orders.save(updated)
    return { accepted: true, outcome, summary }
  }
}

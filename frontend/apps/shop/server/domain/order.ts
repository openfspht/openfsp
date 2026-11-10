import {
  EVENT_TYPES,
  TERMINAL_STATUSES,
  type EventType,
  type Money,
  type NextAction,
  type Payment,
  type PaymentStatus,
} from "@workspace/types"

export interface ReceivedEvent {
  id: string
  type: EventType
  sequence: number
  received_at: string
}

export interface OrderLine {
  product_id: string
  name: string
  quantity: number
  unit_price: Money
}

export interface Order {
  reference: string
  lines: OrderLine[]
  amount: Money
  payment_id: string
  provider: string
  status: PaymentStatus
  next_action?: NextAction
  created_at: string
  events: ReceivedEvent[]
}

export type EventOutcome = "applied" | "stale" | "terminal" | "gap"

export const RESYNC_OUTCOMES: ReadonlySet<EventOutcome> = new Set([
  "terminal",
  "gap",
])

const KNOWN_EVENT_TYPES: ReadonlySet<string> = new Set(EVENT_TYPES)

export function isKnownEventType(type: string): type is EventType {
  return KNOWN_EVENT_TYPES.has(type)
}

export function applyPaymentEvent(
  order: Order,
  event: {
    id: string
    type: EventType
    sequence: number
    status: PaymentStatus
    nextAction?: NextAction
  },
  receivedAt: string
): { order: Order; outcome: EventOutcome } {
  const last = Math.max(0, ...order.events.map((it) => it.sequence))
  if (event.sequence <= last) return { order, outcome: "stale" }
  const recorded = {
    ...order,
    events: [
      ...order.events,
      {
        id: event.id,
        type: event.type,
        sequence: event.sequence,
        received_at: receivedAt,
      },
    ],
  }
  if (TERMINAL_STATUSES.has(order.status) && event.status !== order.status)
    return { order: recorded, outcome: "terminal" }
  if (event.sequence > last + 1) return { order: recorded, outcome: "gap" }
  return {
    order: { ...recorded, status: event.status, next_action: event.nextAction },
    outcome: "applied",
  }
}

export function syncWithPayment(order: Order, payment: Payment): Order {
  return { ...order, status: payment.status, next_action: payment.next_action }
}

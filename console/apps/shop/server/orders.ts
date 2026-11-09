import type { WebhookEvent } from "@workspace/openfsp-client"
import { TERMINAL_STATUSES, type EventType, type Money, type PaymentStatus } from "@workspace/types"

export interface Order {
  reference: string
  product: string
  amount: Money
  payment_id: string
  status: PaymentStatus
  created_at: string
  events: { id: string; type: EventType; sequence: number; received_at: string }[]
}

const orders = new Map<string, Order>()
const seenEvents = new Set<string>()
const lastSequence = new Map<string, number>()

export function saveOrder(order: Order) {
  orders.set(order.reference, order)
}

export function listOrders(): Order[] {
  return [...orders.values()].sort((left, right) => right.created_at.localeCompare(left.created_at))
}

export type Applied = "applied" | "duplicate" | "stale" | "unknown-order" | "terminal"

export function applyEvent(event: WebhookEvent): Applied {
  if (seenEvents.has(event.id)) return "duplicate"
  seenEvents.add(event.id)
  if (event.sequence <= (lastSequence.get(event.resource_id) ?? 0)) return "stale"
  lastSequence.set(event.resource_id, event.sequence)

  const order = orders.get(event.reference)
  if (!order) return "unknown-order"
  order.events.push({ id: event.id, type: event.type, sequence: event.sequence, received_at: new Date().toISOString() })
  if (TERMINAL_STATUSES.has(order.status) && event.data.status !== order.status) return "terminal"
  order.status = event.data.status
  return "applied"
}

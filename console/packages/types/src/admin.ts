import type { EventType, Payment, PaymentStatus, Timestamp } from "./openfsp"

export type TransitionSource = "creation" | "lookup" | "signed_callback"

export interface Transition {
  from_status?: PaymentStatus
  to_status: PaymentStatus
  source: TransitionSource
  recorded_at: Timestamp
}

export interface Conflict {
  source: TransitionSource
  reported: { status: PaymentStatus; amount: number }
  recorded_at: Timestamp
}

export interface DeliveryAttempt {
  attempt: number
  started_at: Timestamp
  status_code?: number
  error?: string
  duration_ms: number
}

export type DeliveryStatus = "pending" | "succeeded" | "abandoned" | "stopped"

export interface Delivery {
  endpoint: string
  url: string
  status: DeliveryStatus
  attempts: DeliveryAttempt[]
  next_attempt_at?: Timestamp
}

export interface EventView {
  id: string
  type: EventType
  sequence: number
  created_at: Timestamp
  deliveries: Delivery[]
}

export interface PaymentDetail {
  payment: Payment
  transitions: Transition[]
  conflicts: Conflict[]
  events: EventView[]
}

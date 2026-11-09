import type { EventType, Money, Payment, PaymentStatus, Timestamp } from "./openfsp"

export type TransitionSource = "creation" | "lookup" | "signed_callback" | "attestation"

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
  automatic_sources: boolean
  indeterminate: string[]
  transitions: Transition[]
  conflicts: Conflict[]
  events: EventView[]
}

export type AttestedStatus = "succeeded" | "failed" | "expired" | "canceled"

export interface AttestationRequest {
  person: string
  status: AttestedStatus
  evidence: string
  failure_reason?: string
}

export interface GatewayStatement {
  provider: string
  business_date: string
  final: boolean
  payments: {
    payment: string
    reference: string
    status: PaymentStatus
    amount: Money
    completed_at: Timestamp
    failure_reason?: string
  }[]
  totals: Partial<Record<PaymentStatus, { count: number; amount: Money }>>
}

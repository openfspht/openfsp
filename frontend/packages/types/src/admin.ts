import type { EventType, Money, Payment, PaymentStatus, Timestamp } from "./openfsp"

export type TransitionSource = "creation" | "lookup" | "signed_callback" | "per_payment_url" | "statement" | "attestation"

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

export const SCOPES = ["capabilities:read", "payments:read", "payments:write", "statements:read"] as const

export type ScopeName = (typeof SCOPES)[number]

export interface ApiKeySummary {
  id: string
  principal: string
  prefix: string
  scopes: ScopeName[]
  created_at: Timestamp
  expires_at?: Timestamp
  last_used_at?: Timestamp
  revoked_at?: Timestamp
}

export interface IssuedKey {
  id: string
  secret: string
}

export interface ProviderSummary {
  id: string
  display_name: string
  logo_url: string | null
  capabilities: string[]
}

export type ActivityKind = "transition" | "conflict" | "delivery" | "statement" | "key" | "admin"

export interface ActivityPayment {
  id: string
  reference: string
  provider: string
  amount: Money
  status: PaymentStatus
}

export interface ActivityItem {
  id: string
  at: Timestamp
  kind: ActivityKind
  source?: string
  payment?: ActivityPayment
  from_status?: PaymentStatus
  to_status?: PaymentStatus
  event_type?: EventType
  endpoint?: string
  status_code?: number
  attempt?: number
  duration_ms?: number
  subject?: string
  result?: string
}

export interface StatusTotal {
  status: PaymentStatus
  count: number
  amount: Money
}

export interface Overview {
  environment: string
  business_date: string
  payments: StatusTotal[]
  retrying_deliveries: number
  disabled_endpoints: number
  active_keys: number
}

export interface AuditRecord {
  recorded_at: Timestamp
  kind: string
  principal_id?: string
  lookup_prefix?: string
  operation: string
  request_id?: string
  result: string
  detail?: string
}

export interface WebhookEndpoint {
  name: string
  url: string
  events: EventType[]
  enabled: boolean
  managed: boolean
  pending: number
  disabled_at?: Timestamp
  reason?: string
}

export interface SigningKeyView {
  kid: string
  status: "active" | "retired"
  activates_at: Timestamp
  retired_at?: Timestamp
}

export interface SigningKeys {
  keys: SigningKeyView[]
  rotate_every_days?: number
  next_rotation_at?: Timestamp
}

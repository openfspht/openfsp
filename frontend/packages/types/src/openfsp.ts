export type Timestamp = string

export type Currency = "HTG"

export interface Money {
  amount: number
  currency: Currency
}

export const PAYMENT_STATUSES = ["pending", "succeeded", "failed", "expired", "canceled"] as const

export type PaymentStatus = (typeof PAYMENT_STATUSES)[number]

export const TERMINAL_STATUSES: ReadonlySet<PaymentStatus> = new Set(["succeeded", "failed", "expired", "canceled"])

export type FailureReason =
  | "declined"
  | "insufficient_funds"
  | "payer_canceled"
  | "payer_unreachable"
  | "limit_exceeded"
  | "rejected_by_provider"
  | "provider_error"
  | "unspecified"

export type NextAction =
  | { type: "redirect"; redirect_url: string; expires_at?: Timestamp }
  | { type: "payer_approval" }
  | { type: "none" }

export interface Payment {
  id: string
  reference: string
  status: PaymentStatus
  amount: Money
  provider: string
  provider_reference?: string
  provider_order_id: string
  payer?: { phone_number: string }
  description?: string
  next_action?: NextAction
  created_at: Timestamp
  updated_at: Timestamp
  expires_at?: Timestamp
  completed_at?: Timestamp
  failure_reason?: FailureReason
  failure_detail?: Record<string, unknown>
  metadata?: Record<string, string>
}

export interface PageInfo {
  number: number
  size: number
  total_elements: number
  total_pages: number
}

export interface Page<T> {
  data: T[]
  page: PageInfo
}

export interface Collection<T> {
  data: T[]
}

export const EVENT_TYPES = ["payment.created", "payment.succeeded", "payment.failed", "payment.expired", "payment.canceled"] as const

export type EventType = (typeof EVENT_TYPES)[number]

export interface Problem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  retryable: boolean
  effect: "none" | "unknown"
  request_id: string
  errors?: { field: string; message: string }[]
}

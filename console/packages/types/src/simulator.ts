import type { Timestamp } from "./openfsp"

export const SCENARIOS = [
  "SUCCESS",
  "DECLINE",
  "INSUFFICIENT",
  "TIMEOUT",
  "TIMEOUT_THEN_SUCCESS",
  "TIMEOUT_THEN_NOTHING",
  "PENDING_FOREVER",
  "EXPIRE",
  "NO_CALLBACK",
  "DUPLICATE_CALLBACK",
  "LATE_CALLBACK",
  "UNKNOWN_STATUS",
  "CREDENTIAL_ECHO",
  "CONTRADICT",
  "PROVIDER_ERROR",
  "AMOUNT_MISMATCH",
] as const

export type Scenario = (typeof SCENARIOS)[number]

export const SIMULATED_OPERATORS = ["mock_alpha", "mock_beta"] as const

export type SimulatedOperator = (typeof SIMULATED_OPERATORS)[number]

export type Outcome = "SUCCESSFUL" | "FAILED" | "EXPIRED"

export interface SimulatedPayment {
  operator: string
  payment_id: string
  order_id: string
  amount: string
  msisdn?: string
  callback_url?: string
  scenario: Scenario
  created_at: Timestamp
  expires_at: Timestamp
  outcome?: Outcome
  reason?: string
  reported_amount?: string
  callbacks_sent: number
  lookups: number
}

export interface JournalEntry {
  operator: string
  received_at: Timestamp
  method: string
  path: string
  body: string
}

import type {
  Collection,
  JournalEntry,
  Outcome,
  Scenario,
  SimulatedOperator,
  SimulatedPayment,
} from "@workspace/types"

import { post, put, request } from "./http"

const CONTROL = "/simulator/control"

export const simulatorControl = {
  payments: () => request<Collection<SimulatedPayment>>(`${CONTROL}/payments`),
  journal: (operator?: SimulatedOperator) =>
    request<Collection<JournalEntry>>(
      `${CONTROL}/journal${operator ? `?operator=${operator}` : ""}`
    ),
  defaultScenario: (operator: SimulatedOperator) =>
    request<{ scenario: Scenario }>(
      `${CONTROL}/operators/${operator}/default-scenario`
    ),
  setDefaultScenario: (operator: SimulatedOperator, scenario: Scenario) =>
    request<void>(
      `${CONTROL}/operators/${operator}/default-scenario`,
      put({ scenario })
    ),
  resolve: (paymentId: string, outcome: Outcome, reason?: string) =>
    request<SimulatedPayment>(
      `${CONTROL}/payments/${paymentId}/resolve`,
      post({ outcome, reason })
    ),
  deliverCallback: (paymentId: string) =>
    request<SimulatedPayment>(
      `${CONTROL}/payments/${paymentId}/callback`,
      post()
    ),
  closeDay: (operator: SimulatedOperator, day: string) =>
    request<void>(
      `${CONTROL}/operators/${operator}/statements/${day}/close`,
      post()
    ),
  advanceClock: (seconds: number) =>
    request<void>(`${CONTROL}/clock/advance`, post({ seconds })),
  reset: () => request<void>(`${CONTROL}/reset`, post()),
}

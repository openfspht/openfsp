import type {
  Collection,
  Outcome,
  Scenario,
  SimulatedOperator,
  SimulatedPayment,
} from "@workspace/types"

import { post, put, request } from "./http"

const CONTROL = "/simulator/control"

export const simulatorControl = {
  payments: () => request<Collection<SimulatedPayment>>(`${CONTROL}/payments`),
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
}

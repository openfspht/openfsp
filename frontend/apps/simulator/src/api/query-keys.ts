import type { SimulatedOperator } from "@workspace/types"

export const queryKeys = {
  defaultScenario: (operator: SimulatedOperator) =>
    ["scenario", operator] as const,
  payments: ["payments"] as const,
  journal: (operator?: SimulatedOperator) => ["journal", operator] as const,
}

export const LIVE = 1000

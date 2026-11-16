import type { SimulatedOperator } from "@workspace/types"

export type QueryParams = Record<string, string | number>

export const queryKeys = {
  scenarios: (operator: SimulatedOperator) =>
    ["simulator", "scenarios", operator] as const,
  defaultScenario: (operator: SimulatedOperator) =>
    ["simulator", "scenario", operator] as const,
  statementDays: (operator: SimulatedOperator) =>
    ["simulator", "statement-days", operator] as const,
  allPayments: ["simulator", "payments"] as const,
  payments: (params: QueryParams) => ["simulator", "payments", params] as const,
  journal: (params: QueryParams) => ["simulator", "journal", params] as const,
}

export const LIVE = 2000

export const queryKeys = {
  payments: ["gateway", "payments"] as const,
  payment: (id: string | undefined) => ["gateway", "payment", id] as const,
  gatewayStatement: (provider: string) =>
    ["gateway", "statement", provider] as const,
  keys: ["gateway", "keys"] as const,
  defaultScenario: (operator: string) =>
    ["simulator", "scenario", operator] as const,
  simulatedPayments: ["simulator", "payments"] as const,
}

export const LIVE = 1000

export const queryKeys = {
  session: ["gateway", "session"] as const,
  payments: ["gateway", "payments"] as const,
  payment: (id: string | undefined) => ["gateway", "payment", id] as const,
  gatewayStatement: (provider: string) =>
    ["gateway", "statement", provider] as const,
  keys: ["gateway", "keys"] as const,
  providers: ["gateway", "providers"] as const,
}

export const LIVE = 1000

export const queryKeys = {
  session: ["gateway", "session"] as const,
  payments: (params: Record<string, string | number>) => ["gateway", "payments", params] as const,
  payment: (id: string | undefined) => ["gateway", "payment", id] as const,
  gatewayStatement: (provider: string) => ["gateway", "statement", provider] as const,
  keys: ["gateway", "keys"] as const,
  providers: ["gateway", "providers"] as const,
  overview: ["gateway", "overview"] as const,
  configuration: ["gateway", "configuration"] as const,
  activity: (filter: string) => ["gateway", "activity", filter] as const,
  audit: (params: Record<string, string | number>) => ["gateway", "audit", params] as const,
  webhookEndpoints: ["gateway", "webhooks", "endpoints"] as const,
}

export const LIVE = 1000

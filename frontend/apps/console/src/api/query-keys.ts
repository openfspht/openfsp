export type QueryParams = Record<string, string | number | readonly string[]>

export const queryKeys = {
    session: ["gateway", "session"] as const,
    allPayments: ["gateway", "payments"] as const,
    payments: (params: QueryParams) => ["gateway", "payments", params] as const,
    payment: (id: string | undefined) => ["gateway", "payment", id] as const,
    gatewayStatement: (provider: string) => ["gateway", "statement", provider] as const,
    allKeys: ["gateway", "keys"] as const,
    keys: (params: QueryParams) => ["gateway", "keys", params] as const,
    providers: ["gateway", "providers"] as const,
    overview: ["gateway", "overview"] as const,
    configuration: ["gateway", "configuration"] as const,
    allActivity: ["gateway", "activity"] as const,
    activity: (params: QueryParams) => ["gateway", "activity", params] as const,
    audit: (params: QueryParams) => ["gateway", "audit", params] as const,
    allWebhookEndpoints: ["gateway", "webhooks", "endpoints"] as const,
    webhookEndpoints: (params: QueryParams) =>
        ["gateway", "webhooks", "endpoints", params] as const,
}

export const LIVE = 10_000
export const PENDING_REFRESH = 3000

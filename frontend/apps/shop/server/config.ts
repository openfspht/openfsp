export interface ShopConfig {
  gatewayUrl: string
  allowPlainHttp: boolean
  apiKey: string
  webhookUrl: string
  providers?: string[]
  publicUrl: string
  host: string
  port: number
}

export function readConfig(
  environment: Record<string, string | undefined>
): ShopConfig {
  const apiKey = environment.OPENFSP_API_KEY
  if (!apiKey)
    throw new Error(
      "OPENFSP_API_KEY is required: use the key the gateway prints on its first start"
    )
  return {
    gatewayUrl: environment.OPENFSP_GATEWAY_URL ?? "http://127.0.0.1:8080",
    allowPlainHttp: environment.OPENFSP_ALLOW_PLAIN_HTTP === "true",
    apiKey,
    webhookUrl:
      environment.SHOP_WEBHOOK_URL ?? "http://127.0.0.1:3000/webhooks/openfsp",
    providers: environment.OPENFSP_PROVIDERS?.split(","),
    publicUrl: environment.SHOP_PUBLIC_URL ?? "http://localhost:5174",
    host: environment.HOST ?? "127.0.0.1",
    port: Number(environment.PORT ?? 3000),
  }
}

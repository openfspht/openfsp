export interface ShopConfig {
  gatewayUrl: string
  apiKey: string
  webhookUrl: string
  providers: string[]
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
    apiKey,
    webhookUrl:
      environment.SHOP_WEBHOOK_URL ?? "http://127.0.0.1:3000/webhooks/openfsp",
    providers: (
      environment.OPENFSP_PROVIDERS ?? "mock_openfsp,mock_moncash,mock_natcash"
    ).split(","),
    publicUrl: environment.SHOP_PUBLIC_URL ?? "http://localhost:5174",
    host: environment.HOST ?? "127.0.0.1",
    port: Number(environment.PORT ?? 3000),
  }
}

import type { WireJwk } from "../protocol/wire.js"

export type WebhookKey = Readonly<WireJwk>

export interface ServerConfiguration {
    readonly issuer: string
    readonly protocolVersion: string
    readonly capabilitiesSupported: readonly string[]
    readonly apiBase?: string
    readonly paymentsEndpoint?: string
    readonly capabilitiesEndpoint?: string
    readonly statementsEndpoint?: string
    readonly webhookKeys: readonly WebhookKey[]
    readonly authenticationMethodsSupported: readonly string[]
    readonly scopesSupported: readonly string[]
    readonly webhookSignatureAlgorithmsSupported: readonly string[]
    readonly conformanceEndpoint?: string
}

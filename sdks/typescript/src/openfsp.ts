import { OpenFspConfig, type OpenFspOptions } from "./config.js"
import { Api, systemClock, type Clock } from "./http/api.js"
import { FetchTransport, type Transport } from "./http/transport.js"
import type { ServerConfiguration } from "./models/configuration.js"
import type { Provider } from "./models/provider.js"
import { Payments } from "./payments.js"
import { toConfiguration, toProvider } from "./protocol/mapping.js"
import { requireArray, requireShape } from "./protocol/shape.js"
import { Statements } from "./statements.js"
import type { WireCapabilities, WireConfiguration, WireProvider } from "./protocol/wire.js"
import { Webhooks, type WebhookOptions } from "./webhooks/webhooks.js"

const PROVIDERS_TTL_MS = 60_000
const PROVIDERS_MAX_TTL_MS = 3_600_000
const PROVIDER_FIELDS = ["id", "display_name", "capabilities", "payments"] as const
const PROVIDER_PAYMENT_FIELDS = [
    "next_actions",
    "payer_required",
    "return_url_required",
    "expiry_guaranteed",
    "locales",
] as const

export interface OpenFspDependencies {
    readonly transport?: Transport
    readonly clock?: Clock
    readonly webhooks?: Omit<WebhookOptions, "url">
}

export class OpenFsp {
    readonly config: OpenFspConfig
    readonly payments: Payments
    readonly statements: Statements
    readonly webhooks: Webhooks
    private readonly api: Api
    private readonly clock: Clock
    private cachedProviders:
        { readonly providers: Provider[]; readonly freshUntil: number } | undefined

    constructor(config: OpenFspConfig | OpenFspOptions, dependencies: OpenFspDependencies = {}) {
        this.config = config instanceof OpenFspConfig ? config : OpenFspConfig.from(config)
        this.clock = dependencies.clock ?? systemClock
        this.api = new Api(this.config, dependencies.transport ?? new FetchTransport(), this.clock)
        this.payments = new Payments(this.api, this.config)
        this.statements = new Statements(this.api)
        this.webhooks = new Webhooks(async () => (await this.configuration()).webhookKeys, {
            ...dependencies.webhooks,
            url: this.config.webhookUrl,
        })
    }

    static fromEnv(
        environment?: Readonly<Record<string, string | undefined>>,
        dependencies: OpenFspDependencies = {}
    ): OpenFsp {
        return new OpenFsp(OpenFspConfig.fromEnv(environment), dependencies)
    }

    async providers(): Promise<Provider[]> {
        const now = this.clock.now()
        if (this.cachedProviders !== undefined && now < this.cachedProviders.freshUntil) {
            return [...this.cachedProviders.providers]
        }
        const fetched = await this.api.fetch<WireCapabilities>({
            method: "GET",
            path: "v1/capabilities",
        })
        const providers = requireArray(
            requireShape(fetched.body, ["providers"]).providers,
            "providers"
        ).map((wire) => toProvider(checkedProvider(wire)))
        const lifetime = Math.min(fetched.maxAgeMs ?? PROVIDERS_TTL_MS, PROVIDERS_MAX_TTL_MS)
        this.cachedProviders = { providers, freshUntil: now + lifetime }
        return [...providers]
    }

    async provider(id: string): Promise<Provider | undefined> {
        return (await this.providers()).find((provider) => provider.id === id)
    }

    async configuration(): Promise<ServerConfiguration> {
        return toConfiguration(
            requireShape(
                await this.api.call<WireConfiguration>({
                    method: "GET",
                    path: ".well-known/openfsp-configuration",
                    authenticated: false,
                }),
                ["issuer", "protocol_version", "capabilities_supported"]
            )
        )
    }

    toString(): string {
        return `OpenFsp(${this.config.baseUrl.href})`
    }

    toJSON(): ReturnType<OpenFspConfig["toJSON"]> {
        return this.config.toJSON()
    }
}

function checkedProvider(wire: WireProvider): WireProvider {
    const provider = requireShape(wire, PROVIDER_FIELDS)
    const payments = requireShape(provider.payments, PROVIDER_PAYMENT_FIELDS)
    requireArray(provider.capabilities, "capabilities")
    requireArray(payments.next_actions, "next_actions")
    requireArray(payments.locales, "locales")
    return provider
}

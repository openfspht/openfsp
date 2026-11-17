import { OpenFspConfig, type OpenFspOptions } from "./config.js"
import { Api, systemClock, type Clock } from "./http/api.js"
import { FetchTransport, type Transport } from "./http/transport.js"
import type { ServerConfiguration } from "./models/configuration.js"
import type { Provider } from "./models/provider.js"
import { Payments } from "./payments.js"
import { toConfiguration, toProvider } from "./protocol/mapping.js"
import { requireShape } from "./protocol/shape.js"
import type { WireCapabilities, WireConfiguration } from "./protocol/wire.js"
import { Webhooks, type WebhookOptions } from "./webhooks/webhooks.js"

const PROVIDERS_TTL_MS = 60_000

export interface OpenFspDependencies {
    readonly transport?: Transport
    readonly clock?: Clock
    readonly webhooks?: Omit<WebhookOptions, "url">
}

export class OpenFsp {
    readonly config: OpenFspConfig
    readonly payments: Payments
    readonly webhooks: Webhooks
    private readonly api: Api
    private readonly clock: Clock
    private cachedProviders:
        { readonly providers: Provider[]; readonly fetchedAt: number } | undefined

    constructor(config: OpenFspConfig | OpenFspOptions, dependencies: OpenFspDependencies = {}) {
        this.config = config instanceof OpenFspConfig ? config : OpenFspConfig.from(config)
        this.clock = dependencies.clock ?? systemClock
        this.api = new Api(this.config, dependencies.transport ?? new FetchTransport(), this.clock)
        this.payments = new Payments(this.api)
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
        if (
            this.cachedProviders !== undefined &&
            now < this.cachedProviders.fetchedAt + PROVIDERS_TTL_MS
        ) {
            return [...this.cachedProviders.providers]
        }
        const capabilities = requireShape(
            await this.api.call<WireCapabilities>({ method: "GET", path: "v1/capabilities" }),
            ["providers"]
        )
        const providers = capabilities.providers.map(toProvider)
        this.cachedProviders = { providers, fetchedAt: now }
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

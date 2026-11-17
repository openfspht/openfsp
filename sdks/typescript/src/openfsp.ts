import { OpenFspConfig, type OpenFspOptions } from "./config.js"
import { Api, systemClock, type Clock } from "./http/api.js"
import { FetchTransport, type Transport } from "./http/transport.js"
import type { Provider } from "./models/provider.js"
import { Payments } from "./payments.js"
import { toProvider } from "./protocol/mapping.js"
import { requireShape } from "./protocol/shape.js"
import type { WireCapabilities, WireConfiguration } from "./protocol/wire.js"
import { Webhooks, type WebhookOptions } from "./webhooks/webhooks.js"

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

    constructor(config: OpenFspConfig | OpenFspOptions, dependencies: OpenFspDependencies = {}) {
        this.config = config instanceof OpenFspConfig ? config : OpenFspConfig.from(config)
        this.api = new Api(
            this.config,
            dependencies.transport ?? new FetchTransport(),
            dependencies.clock ?? systemClock
        )
        this.payments = new Payments(this.api)
        this.webhooks = new Webhooks(async () => (await this.configuration()).webhook_keys ?? [], {
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
        const capabilities = requireShape(
            await this.api.call<WireCapabilities>({ method: "GET", path: "v1/capabilities" }),
            ["providers"]
        )
        return capabilities.providers.map(toProvider)
    }

    async provider(id: string): Promise<Provider | undefined> {
        return (await this.providers()).find((provider) => provider.id === id)
    }

    async configuration(): Promise<WireConfiguration> {
        return requireShape(
            await this.api.call<WireConfiguration>({
                method: "GET",
                path: ".well-known/openfsp-configuration",
                authenticated: false,
            }),
            ["issuer", "protocol_version"]
        )
    }

    toString(): string {
        return `OpenFsp(${this.config.baseUrl.href})`
    }

    toJSON(): ReturnType<OpenFspConfig["toJSON"]> {
        return this.config.toJSON()
    }
}

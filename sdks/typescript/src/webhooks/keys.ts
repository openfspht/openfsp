import { WebhookKeysUnavailableError } from "../errors.js"
import type { WebhookKey } from "../models/configuration.js"

export type KeySource = () => Promise<readonly WebhookKey[]>

const REFRESH_INTERVAL_SECONDS = 10
const FAILURE_BACKOFF_SECONDS = 1
const USABLE_STATUSES: ReadonlySet<string> = new Set(["active", "retired"])

export class WebhookKeys {
    private keys = new Map<string, CryptoKey>()
    private refreshedAt: number | undefined
    private failedAt: number | undefined
    private refreshing: Promise<void> | undefined
    private readonly source: KeySource
    private readonly now: () => number

    constructor(source: KeySource, now: () => number) {
        this.source = source
        this.now = now
    }

    async find(keyId: string): Promise<CryptoKey | undefined> {
        const known = this.keys.get(keyId)
        if (known !== undefined) {
            return known
        }
        if (this.refreshing === undefined) {
            if (
                this.failedAt !== undefined &&
                this.now() < this.failedAt + FAILURE_BACKOFF_SECONDS
            ) {
                throw unavailable()
            }
            if (
                this.refreshedAt !== undefined &&
                this.now() < this.refreshedAt + REFRESH_INTERVAL_SECONDS
            ) {
                return undefined
            }
            this.refreshing = this.refresh().finally(() => (this.refreshing = undefined))
        }
        await this.refreshing
        return this.keys.get(keyId)
    }

    private async refresh(): Promise<void> {
        const startedAt = this.now()
        const usable = new Map<string, CryptoKey>()
        for (const jwk of await this.load()) {
            if (
                jwk.kty !== "OKP" ||
                jwk.crv !== "Ed25519" ||
                jwk.x === undefined ||
                !USABLE_STATUSES.has(jwk.openfsp_status ?? "")
            ) {
                continue
            }
            const key = await importKey(jwk.x)
            if (key !== undefined) {
                usable.set(jwk.kid, key)
            }
        }
        this.keys = usable
        this.refreshedAt = startedAt
        this.failedAt = undefined
    }

    private async load(): Promise<readonly WebhookKey[]> {
        try {
            return await this.source()
        } catch (failure) {
            this.failedAt = this.now()
            throw unavailable(failure)
        }
    }
}

function unavailable(cause?: unknown): WebhookKeysUnavailableError {
    return new WebhookKeysUnavailableError("the webhook keys could not be loaded", { cause })
}

async function importKey(x: string): Promise<CryptoKey | undefined> {
    try {
        return await crypto.subtle.importKey(
            "jwk",
            { kty: "OKP", crv: "Ed25519", x },
            { name: "Ed25519" },
            false,
            ["verify"]
        )
    } catch {
        return undefined
    }
}

import type { WireJwk } from "../protocol/wire.js"

export type KeySource = () => Promise<readonly WireJwk[]>

const REFRESH_INTERVAL_SECONDS = 10
const USABLE_STATUSES: ReadonlySet<string> = new Set(["active", "retired"])

export class WebhookKeys {
  private keys = new Map<string, CryptoKey>()
  private refreshedAt: number | undefined
  private refreshing: Promise<void> | undefined
  private readonly source: KeySource
  private readonly now: () => number

  constructor(source: KeySource, now: () => number) {
    this.source = source
    this.now = now
  }

  async find(keyId: string): Promise<CryptoKey | undefined> {
    const known = this.keys.get(keyId)
    if (known !== undefined) return known
    if (this.refreshing === undefined) {
      if (this.refreshedAt !== undefined && this.now() < this.refreshedAt + REFRESH_INTERVAL_SECONDS) return undefined
      this.refreshing = this.refresh().finally(() => (this.refreshing = undefined))
    }
    await this.refreshing
    return this.keys.get(keyId)
  }

  private async refresh(): Promise<void> {
    this.refreshedAt = this.now()
    const usable = new Map<string, CryptoKey>()
    for (const jwk of await this.source()) {
      if (jwk.kty !== "OKP" || jwk.crv !== "Ed25519" || jwk.x === undefined || !USABLE_STATUSES.has(jwk.openfsp_status ?? "")) continue
      const key = await importKey(jwk.x)
      if (key !== undefined) usable.set(jwk.kid, key)
    }
    this.keys = usable
  }
}

async function importKey(x: string): Promise<CryptoKey | undefined> {
  try {
    return await crypto.subtle.importKey("jwk", { kty: "OKP", crv: "Ed25519", x }, { name: "Ed25519" }, false, ["verify"])
  } catch {
    return undefined
  }
}

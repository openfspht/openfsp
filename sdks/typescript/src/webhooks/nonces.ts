export interface NonceStore {
  reserve(nonce: string, expiresAt: number): boolean | Promise<boolean>
  release(nonce: string): void | Promise<void>
}

const SWEEP_INTERVAL_SECONDS = 60

export class InMemoryNonceStore implements NonceStore {
  private readonly nonces = new Map<string, number>()
  private readonly now: () => number
  private sweptAt = 0

  constructor(now: () => number = () => Math.floor(Date.now() / 1000)) {
    this.now = now
  }

  reserve(nonce: string, expiresAt: number): boolean {
    this.sweep()
    if (this.nonces.has(nonce)) return false
    this.nonces.set(nonce, expiresAt)
    return true
  }

  release(nonce: string): void {
    this.nonces.delete(nonce)
  }

  private sweep(): void {
    const now = this.now()
    if (now - this.sweptAt < SWEEP_INTERVAL_SECONDS) return
    this.sweptAt = now
    for (const [nonce, expiresAt] of this.nonces) if (expiresAt < now) this.nonces.delete(nonce)
  }
}

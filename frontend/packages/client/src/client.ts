import type { Collection, Money, Payment, Problem, ProviderSummary } from "@workspace/types"

export interface CreatePaymentRequest {
  reference: string
  amount: Money
  provider: string
  payer?: { phone_number: string }
  description?: string
  return_url?: string
  metadata?: Record<string, string>
}

export class OpenFSPError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(`${problem.title} (${problem.type})`)
    this.problem = problem
  }
}

export class OpenFSPClient {
  private readonly baseUrl: string
  private readonly apiKey: string

  constructor(baseUrl: string, apiKey: string) {
    this.baseUrl = baseUrl
    this.apiKey = apiKey
  }

  createPayment(request: CreatePaymentRequest, idempotencyKey: string = crypto.randomUUID()): Promise<Payment> {
    return this.call("POST", "/v1/payments", request, { "Idempotency-Key": idempotencyKey })
  }

  getPayment(id: string): Promise<Payment> {
    return this.call("GET", `/v1/payments/${encodeURIComponent(id)}`)
  }

  async findPayment(reference: string): Promise<Payment | undefined> {
    const found = await this.call<Collection<Payment>>("GET", `/v1/payments?reference=${encodeURIComponent(reference)}`)
    return found.data[0]
  }

  async providers(): Promise<ProviderSummary[]> {
    const capabilities = await this.call<{ providers: ProviderSummary[] }>("GET", "/v1/capabilities")
    return capabilities.providers
  }

  synchronizePayment(id: string): Promise<Payment> {
    return this.call("POST", `/v1/payments/${encodeURIComponent(id)}/synchronize`)
  }

  private async call<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
    const response = await fetch(new URL(path, this.baseUrl), {
      method,
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const payload = await response.json()
    if (!response.ok) throw new OpenFSPError(payload as Problem)
    return payload as T
  }
}

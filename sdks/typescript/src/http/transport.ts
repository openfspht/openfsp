import { TransportError } from "../errors.js"

export interface HttpRequest {
  readonly method: "GET" | "POST"
  readonly url: URL
  readonly headers: Headers
  readonly body?: string
  readonly timeoutMs: number
}

export interface HttpResponse {
  readonly status: number
  readonly headers: Headers
  readonly body: string
}

export interface Transport {
  send(request: HttpRequest): Promise<HttpResponse>
}

export class FetchTransport implements Transport {
  private readonly fetch: typeof globalThis.fetch

  constructor(fetch: typeof globalThis.fetch = globalThis.fetch) {
    this.fetch = fetch
  }

  async send(request: HttpRequest): Promise<HttpResponse> {
    try {
      const response = await this.fetch(request.url, {
        method: request.method,
        headers: request.headers,
        body: request.body,
        redirect: "manual",
        signal: AbortSignal.timeout(request.timeoutMs),
      })
      return { status: response.status, headers: response.headers, body: await response.text() }
    } catch (error) {
      throw new TransportError(`${request.method} ${request.url.pathname} ${describe(error, request.timeoutMs)}`, { cause: error })
    }
  }
}

function describe(error: unknown, timeoutMs: number): string {
  if (error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError")) return `timed out after ${timeoutMs} ms`
  return `failed: ${error instanceof Error ? error.message : String(error)}`
}

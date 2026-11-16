import { ConfigurationError } from "./errors.js"
import { DEFAULT_RETRY_POLICY, type RetryPolicy } from "./http/retry.js"

export interface OpenFspOptions {
  readonly baseUrl: string
  readonly apiKey: string
  readonly timeoutMs?: number
  readonly retryPolicy?: RetryPolicy
  readonly webhookUrl?: string
  /** Sends the API key in clear text to a non-loopback host: development networks only. */
  readonly allowPlainHttp?: boolean
}

type Environment = Readonly<Record<string, string | undefined>>

const DEFAULT_PREFIX = "OPENFSP_"
const DEFAULT_TIMEOUT_MS = 30_000
const LOOPBACK_V4 = /^127(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/

export class OpenFspConfig {
  readonly baseUrl: URL
  readonly timeoutMs: number
  readonly retryPolicy: RetryPolicy
  readonly webhookUrl: string | undefined
  readonly #apiKey: string

  private constructor(baseUrl: URL, apiKey: string, timeoutMs: number, retryPolicy: RetryPolicy, webhookUrl: string | undefined) {
    this.baseUrl = baseUrl
    this.#apiKey = apiKey
    this.timeoutMs = timeoutMs
    this.retryPolicy = retryPolicy
    this.webhookUrl = webhookUrl
    Object.freeze(this)
  }

  static from(options: OpenFspOptions): OpenFspConfig {
    return new OpenFspConfig(
      parseBaseUrl(options.baseUrl, options.allowPlainHttp === true),
      parseApiKey(options.apiKey),
      parseTimeout(options.timeoutMs ?? DEFAULT_TIMEOUT_MS),
      parseRetryPolicy(options.retryPolicy ?? DEFAULT_RETRY_POLICY),
      options.webhookUrl === undefined ? undefined : parseWebhookUrl(options.webhookUrl),
    )
  }

  static fromEnv(environment: Environment = processEnvironment(), prefix = DEFAULT_PREFIX): OpenFspConfig {
    const read = (name: string) => environment[`${prefix}${name}`]
    const timeout = read("TIMEOUT_MS")
    return OpenFspConfig.from({
      baseUrl: required(read("BASE_URL"), `${prefix}BASE_URL`),
      apiKey: required(read("API_KEY"), `${prefix}API_KEY`),
      timeoutMs: timeout === undefined ? undefined : Number(timeout),
      webhookUrl: read("WEBHOOK_URL"),
      allowPlainHttp: read("ALLOW_PLAIN_HTTP") === "true",
    })
  }

  get apiKey(): string {
    return this.#apiKey
  }

  toJSON(): { baseUrl: string; timeoutMs: number } {
    return { baseUrl: this.baseUrl.href, timeoutMs: this.timeoutMs }
  }
}

function processEnvironment(): Environment {
  return (globalThis as { process?: { env: Environment } }).process?.env ?? {}
}

function required(value: string | undefined, name: string): string {
  if (value === undefined || value.trim() === "") throw new ConfigurationError(`${name} is required`)
  return value
}

function parseApiKey(apiKey: string): string {
  if (typeof apiKey !== "string" || apiKey === "" || /[\s\p{Cc}]/u.test(apiKey)) throw new ConfigurationError("apiKey must be a non-empty token without whitespace")
  return apiKey
}

function parseTimeout(timeoutMs: number): number {
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new ConfigurationError("timeoutMs must be a positive number")
  return timeoutMs
}

function parseRetryPolicy(policy: RetryPolicy): RetryPolicy {
  if (!Number.isInteger(policy.maxAttempts) || policy.maxAttempts < 1) throw new ConfigurationError("retryPolicy.maxAttempts must be an integer of at least 1")
  if ([policy.initialBackoffMs, policy.maxBackoffMs, policy.maxRetryAfterMs].some((duration) => !(duration >= 0))) {
    throw new ConfigurationError("retryPolicy durations must not be negative")
  }
  return Object.freeze({ ...policy })
}

function parseBaseUrl(value: string, allowPlainHttp: boolean): URL {
  const url = parseUrl(value, "baseUrl")
  if (url.username || url.password || url.search || url.hash) throw new ConfigurationError("baseUrl must not carry credentials, a query or a fragment")
  if (url.protocol === "http:" && !allowPlainHttp && !isLoopback(url.hostname)) {
    throw new ConfigurationError("baseUrl must use https outside a loopback host; set allowPlainHttp for a development network")
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") throw new ConfigurationError("baseUrl must use https")
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/`
  return url
}

function parseWebhookUrl(value: string): string {
  return parseUrl(value, "webhookUrl").href
}

function parseUrl(value: string, name: string): URL {
  try {
    return new URL(value.trim())
  } catch {
    throw new ConfigurationError(`${name} must be an absolute URL`)
  }
}

function isLoopback(hostname: string): boolean {
  const name = hostname.replace(/^\[|\]$/g, "").toLowerCase()
  return name === "localhost" || name.endsWith(".localhost") || name === "::1" || LOOPBACK_V4.test(name)
}

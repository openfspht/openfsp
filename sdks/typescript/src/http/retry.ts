import { ApiError, TransportError, type OpenFspError } from "../errors.js"

export interface RetryPolicy {
    readonly maxAttempts: number
    readonly initialBackoffMs: number
    readonly maxBackoffMs: number
    readonly maxRetryAfterMs: number
}

export const DEFAULT_RETRY_POLICY: RetryPolicy = Object.freeze({
    maxAttempts: 3,
    initialBackoffMs: 500,
    maxBackoffMs: 8_000,
    maxRetryAfterMs: 30_000,
})

export const NO_RETRY: RetryPolicy = Object.freeze({ ...DEFAULT_RETRY_POLICY, maxAttempts: 1 })

const RESENDABLE_CLIENT_ERRORS: ReadonlySet<string> = new Set([
    "rate-limited",
    "idempotency-request-in-progress",
])

export function retryDelay(
    policy: RetryPolicy,
    failure: OpenFspError,
    attempt: number,
    random: number
): number | undefined {
    if (attempt >= policy.maxAttempts || !isResendable(failure)) {
        return undefined
    }
    const ceiling = Math.min(policy.initialBackoffMs * 2 ** (attempt - 1), policy.maxBackoffMs)
    const backoff = Math.floor(ceiling / 2 + (random * ceiling) / 2)
    if (!(failure instanceof ApiError) || failure.retryAfterMs === undefined) {
        return backoff
    }
    return failure.retryAfterMs > policy.maxRetryAfterMs
        ? undefined
        : Math.max(backoff, failure.retryAfterMs)
}

function isResendable(failure: OpenFspError): boolean {
    if (failure instanceof TransportError) {
        return true
    }
    return (
        failure instanceof ApiError &&
        failure.retryable &&
        (failure.status >= 500 || RESENDABLE_CLIENT_ERRORS.has(failure.code))
    )
}

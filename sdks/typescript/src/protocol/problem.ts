import { ApiError, PaymentNotFoundError, type FieldError } from "../errors.js"
import type { HttpResponse } from "../http/transport.js"
import { Money } from "../models/money.js"

const PROBLEM_BASE_URI = "https://openfsp.org/problems/"
const GATEWAY_ERRORS: ReadonlySet<number> = new Set([502, 503, 504])

export function toApiError(response: HttpResponse, now: number): ApiError {
    const requestId = response.headers.get("Request-Id") ?? undefined
    const retryAfterMs = parseRetryAfter(response.headers.get("Retry-After"), now)
    const problem = parseObject(response.body)
    const type = problem?.type
    if (problem === undefined || typeof type !== "string") {
        return new ApiError({
            status: response.status,
            code: "unexpected-response",
            title: "The server did not answer with a problem document",
            retryable: GATEWAY_ERRORS.has(response.status),
            effect: "unknown",
            requestId,
            retryAfterMs,
        })
    }
    const code = type.startsWith(PROBLEM_BASE_URI) ? type.slice(PROBLEM_BASE_URI.length) : type
    const details = {
        status: response.status,
        code,
        title: typeof problem.title === "string" ? problem.title : code,
        detail: typeof problem.detail === "string" ? problem.detail : undefined,
        retryable: problem.retryable === true,
        effect: problem.effect === "none" ? ("none" as const) : ("unknown" as const),
        requestId: typeof problem.request_id === "string" ? problem.request_id : requestId,
        fieldErrors: Array.isArray(problem.errors)
            ? problem.errors.filter(isFieldError).map(toFieldError)
            : [],
        retryAfterMs,
        instance: typeof problem.instance === "string" ? problem.instance : undefined,
        minAmount: toMoney(problem.min_amount),
        maxAmount: toMoney(problem.max_amount),
        amountStep: toMoney(problem.amount_step),
    }
    return code === "not-found" ? new PaymentNotFoundError(details) : new ApiError(details)
}

export function parseObject(body: string): Record<string, unknown> | undefined {
    try {
        const value: unknown = JSON.parse(body)
        return typeof value === "object" && value !== null && !Array.isArray(value)
            ? (value as Record<string, unknown>)
            : undefined
    } catch {
        return undefined
    }
}

function parseRetryAfter(value: string | null, now: number): number | undefined {
    if (value === null) {
        return undefined
    }
    const trimmed = value.trim()
    if (/^\d+$/.test(trimmed)) {
        return Number(trimmed) * 1000
    }
    const at = Date.parse(trimmed)
    return Number.isNaN(at) ? undefined : Math.max(at - now, 0)
}

function toMoney(value: unknown): Money | undefined {
    const money = value as { amount?: unknown; currency?: unknown } | null | undefined
    return typeof money === "object" &&
        money !== null &&
        money.currency === "HTG" &&
        Number.isSafeInteger(money.amount)
        ? Money.centimes(money.amount as number)
        : undefined
}

function isFieldError(value: unknown): value is { field: string; code: string; detail?: unknown } {
    return (
        typeof value === "object" &&
        value !== null &&
        typeof (value as { field?: unknown }).field === "string" &&
        typeof (value as { code?: unknown }).code === "string"
    )
}

function toFieldError(value: { field: string; code: string; detail?: unknown }): FieldError {
    return typeof value.detail === "string"
        ? { field: value.field, code: value.code, detail: value.detail }
        : { field: value.field, code: value.code }
}

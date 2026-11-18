import type { Money } from "./models/money.js"

export type Effect = "none" | "unknown"

export class OpenFspError extends Error {
    override readonly name: string = "OpenFspError"
}

export class ConfigurationError extends OpenFspError {
    override readonly name = "ConfigurationError"
}

export class InvalidRequestError extends OpenFspError {
    override readonly name = "InvalidRequestError"
}

export class InvalidSignatureError extends OpenFspError {
    override readonly name = "InvalidSignatureError"
}

export class WebhookKeysUnavailableError extends OpenFspError {
    override readonly name = "WebhookKeysUnavailableError"
}

export class TransportError extends OpenFspError {
    override readonly name = "TransportError"
    readonly effect: Effect = "unknown"
}

export interface FieldError {
    readonly field: string
    readonly code: string
    readonly detail?: string
}

export interface ApiErrorDetails {
    readonly status: number
    readonly code: string
    readonly title: string
    readonly detail?: string
    readonly retryable: boolean
    readonly effect: Effect
    readonly requestId?: string
    readonly fieldErrors?: readonly FieldError[]
    readonly retryAfterMs?: number
    readonly instance?: string
    readonly minAmount?: Money
    readonly maxAmount?: Money
    readonly amountStep?: Money
}

export class ApiError extends OpenFspError {
    override readonly name: string = "ApiError"
    readonly status: number
    readonly code: string
    readonly title: string
    readonly detail: string | undefined
    readonly retryable: boolean
    readonly effect: Effect
    readonly requestId: string | undefined
    readonly fieldErrors: readonly FieldError[]
    readonly retryAfterMs: number | undefined
    readonly instance: string | undefined
    readonly minAmount: Money | undefined
    readonly maxAmount: Money | undefined
    readonly amountStep: Money | undefined

    constructor(details: ApiErrorDetails) {
        super(
            `${details.status} ${details.code}${details.requestId ? ` (request ${details.requestId})` : ""}`
        )
        this.status = details.status
        this.code = details.code
        this.title = details.title
        this.detail = details.detail
        this.retryable = details.retryable
        this.effect = details.effect
        this.requestId = details.requestId
        this.fieldErrors = details.fieldErrors ?? []
        this.retryAfterMs = details.retryAfterMs
        this.instance = details.instance
        this.minAmount = details.minAmount
        this.maxAmount = details.maxAmount
        this.amountStep = details.amountStep
    }
}

export class PaymentNotFoundError extends ApiError {
    override readonly name = "PaymentNotFoundError"
}

export class UnknownPaymentStatusError extends ApiError {
    override readonly name = "UnknownPaymentStatusError"
    readonly paymentId: string
    readonly reference: string
    readonly unknownStatus: string

    constructor(paymentId: string, reference: string, unknownStatus: string) {
        super({
            status: 200,
            code: "unexpected-response",
            title: `unknown payment status ${unknownStatus}`,
            retryable: false,
            effect: "unknown",
        })
        this.paymentId = paymentId
        this.reference = reference
        this.unknownStatus = unknownStatus
    }
}

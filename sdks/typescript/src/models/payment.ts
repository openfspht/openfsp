import type { Money } from "./money.js"

export const PAYMENT_STATUSES = Object.freeze([
    "pending",
    "succeeded",
    "failed",
    "expired",
    "canceled",
] as const)

export type PaymentStatus = (typeof PAYMENT_STATUSES)[number]

export function isPaymentStatus(value: unknown): value is PaymentStatus {
    return (PAYMENT_STATUSES as readonly unknown[]).includes(value)
}

export function isTerminalStatus(status: PaymentStatus): boolean {
    return status !== "pending"
}

export const FAILURE_REASONS = Object.freeze([
    "declined",
    "insufficient_funds",
    "payer_canceled",
    "payer_unreachable",
    "limit_exceeded",
    "rejected_by_provider",
    "provider_error",
    "unspecified",
] as const)

export type FailureReason = (typeof FAILURE_REASONS)[number]

export function toFailureReason(value: string): FailureReason {
    return (FAILURE_REASONS as readonly string[]).includes(value)
        ? (value as FailureReason)
        : "unspecified"
}

export type Locale = "fr" | "ht" | "en"

export type NextAction =
    | { readonly type: "redirect"; readonly url: string; readonly expiresAt?: Date }
    | { readonly type: "payer_approval" }
    | { readonly type: "none" }

export interface Fee {
    readonly amount: Money
    readonly bearer: "merchant" | "payer"
}

export interface PaymentFields {
    readonly id: string
    readonly reference: string
    readonly status: PaymentStatus
    readonly amount: Money
    readonly provider: string
    readonly payerPhoneNumber?: string
    readonly description?: string
    readonly nextAction: NextAction
    readonly createdAt: Date
    readonly updatedAt: Date
    readonly expiresAt?: Date
    readonly completedAt?: Date
    readonly failureReason?: FailureReason
    readonly fee?: Fee
    readonly metadata: Readonly<Record<string, string>>
    readonly failureDetail: Readonly<Record<string, unknown>>
}

export interface Payment extends PaymentFields {}

export class Payment {
    constructor(fields: PaymentFields) {
        Object.assign(this, fields)
        Object.freeze(this)
    }

    get isPending(): boolean {
        return this.status === "pending"
    }

    get isSuccessful(): boolean {
        return this.status === "succeeded"
    }

    get isTerminal(): boolean {
        return isTerminalStatus(this.status)
    }

    get requiresRedirect(): boolean {
        return this.isPending && this.nextAction.type === "redirect"
    }

    get awaitsPayerApproval(): boolean {
        return this.isPending && this.nextAction.type === "payer_approval"
    }

    get redirectUrl(): string | undefined {
        return this.nextAction.type === "redirect" ? this.nextAction.url : undefined
    }

    matches(reference: string, amount: Money, provider: string): boolean {
        return (
            this.reference === reference && this.amount.equals(amount) && this.provider === provider
        )
    }
}

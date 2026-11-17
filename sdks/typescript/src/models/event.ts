import type { Payment, PaymentStatus } from "./payment.js"

export const PAYMENT_EVENT_TYPES = Object.freeze([
    "payment.created",
    "payment.succeeded",
    "payment.failed",
    "payment.expired",
    "payment.canceled",
] as const)

export type PaymentEventType = (typeof PAYMENT_EVENT_TYPES)[number]

export function isPaymentEventType(value: string): value is PaymentEventType {
    return (PAYMENT_EVENT_TYPES as readonly string[]).includes(value)
}

export interface PaymentEventFields {
    readonly id: string
    readonly type: PaymentEventType
    readonly sequence: number
    readonly createdAt: Date
    readonly previousStatus?: PaymentStatus
    readonly payment: Payment
}

export interface PaymentEvent extends PaymentEventFields {}

export class PaymentEvent {
    constructor(fields: PaymentEventFields) {
        Object.assign(this, fields)
        Object.freeze(this)
    }

    get reference(): string {
        return this.payment.reference
    }
}

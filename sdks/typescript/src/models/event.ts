import type { Payment, PaymentStatus } from "./payment.js"

export type PaymentEventType =
    | "payment.created"
    | "payment.succeeded"
    | "payment.failed"
    | "payment.expired"
    | "payment.canceled"

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

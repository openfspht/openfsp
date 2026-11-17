import type { Money } from "./money.js"
import type { Locale } from "./payment.js"

export interface PaymentRequest {
    readonly reference: string
    readonly amount: Money
    readonly provider: string
    readonly payerPhoneNumber?: string
    readonly description?: string
    readonly returnUrl?: string
    readonly callbackUrl?: string
    readonly expiresAt?: Date
    readonly locale?: Locale
    readonly metadata?: Readonly<Record<string, string>>
}

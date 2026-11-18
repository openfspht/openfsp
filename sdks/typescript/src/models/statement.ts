import type { Money } from "./money.js"
import type { Fee } from "./payment.js"

export interface StatementEntry {
    readonly provider: string
    readonly businessDate: string
    readonly bookedAt: Date
    readonly amount: Money
    readonly fee?: Fee
    readonly paymentId?: string
    readonly reference?: string
}

export interface StatementPage {
    readonly entries: readonly StatementEntry[]
    readonly nextCursor?: string
}

export interface StatementPaging {
    readonly limit?: number
    readonly cursor?: string
}

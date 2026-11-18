import type { Money } from "./money.js"

export interface StatementEntry {
    readonly type: string
    readonly amount: Money
    readonly feeAmount?: Money
    readonly netAmount?: Money
    readonly bookedAt: Date
    readonly paymentId?: string
    readonly match: string
}

export interface Statement {
    readonly id: string
    readonly provider: string
    readonly businessDate: string
    readonly complete: boolean
    readonly entries: readonly StatementEntry[]
    readonly missingPayments: readonly string[]
}

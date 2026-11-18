import type { Money } from "./money.js"

export interface StatementEntry {
    readonly provider: string
    readonly businessDate: string
    readonly bookedAt: Date
    readonly amount: Money
    readonly feeAmount?: Money
    readonly netAmount?: Money
    readonly paymentId?: string
    readonly reference?: string
}

export interface StatementPage {
    readonly entries: readonly StatementEntry[]
    readonly nextCursor?: string
}

interface StatementPaging {
    readonly provider: string
    readonly limit?: number
    readonly cursor?: string
}

export type StatementQuery = StatementPaging &
    (
        | { readonly businessDate: string; readonly from?: undefined; readonly to?: undefined }
        | { readonly businessDate?: undefined; readonly from: string; readonly to: string }
    )

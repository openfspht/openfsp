import { InvalidRequestError } from "./errors.js"
import type { Api } from "./http/api.js"
import type { StatementPage, StatementPaging } from "./models/statement.js"
import { toStatementEntry } from "./protocol/mapping.js"
import { requireArray, requireShape } from "./protocol/shape.js"
import type { WireCollection, WireStatementEntry } from "./protocol/wire.js"

const DATE = /^\d{4}-\d{2}-\d{2}$/
const MILLISECONDS_PER_DAY = 86_400_000
const MAX_RANGE_DAYS = 31
const MAX_LIMIT = 100
const ENTRY_FIELDS = ["provider", "business_date", "booked_at", "amount"] as const

export class Statements {
    private readonly api: Api

    constructor(api: Api) {
        this.api = api
    }

    async forDay(
        provider: string,
        businessDate: string,
        paging: StatementPaging = {}
    ): Promise<StatementPage> {
        checkedDay("businessDate", businessDate)
        return this.page(provider, { business_date: businessDate }, paging)
    }

    async between(
        provider: string,
        from: string,
        to: string,
        paging: StatementPaging = {}
    ): Promise<StatementPage> {
        const days = (checkedDay("to", to) - checkedDay("from", from)) / MILLISECONDS_PER_DAY + 1
        if (days < 1) {
            throw new InvalidRequestError("to must not precede from")
        }
        if (days > MAX_RANGE_DAYS) {
            throw new InvalidRequestError(`a range covers at most ${MAX_RANGE_DAYS} days`)
        }
        return this.page(provider, { from, to }, paging)
    }

    private async page(
        provider: string,
        period: Record<string, string>,
        paging: StatementPaging
    ): Promise<StatementPage> {
        const found = await this.api.call<WireCollection<WireStatementEntry>>({
            method: "GET",
            path: "v1/statements",
            query: toWireQuery(provider, period, paging),
        })
        const entries = requireArray(requireShape(found, ["data"]).data, "data").map((entry) =>
            toStatementEntry(requireShape(entry, ENTRY_FIELDS))
        )
        return Object.freeze({
            entries: Object.freeze(entries),
            ...(found.next_cursor == null ? {} : { nextCursor: found.next_cursor }),
        })
    }
}

function toWireQuery(
    provider: string,
    period: Record<string, string>,
    { limit, cursor }: StatementPaging
): Record<string, string> {
    if (typeof provider !== "string" || provider.trim() === "") {
        throw new InvalidRequestError("provider must not be empty")
    }
    const wire: Record<string, string> = { provider, ...period }
    if (limit !== undefined) {
        if (!Number.isInteger(limit) || limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestError(`limit must be a whole number from 1 to ${MAX_LIMIT}`)
        }
        wire.limit = String(limit)
    }
    if (cursor !== undefined) {
        if (cursor === "") {
            throw new InvalidRequestError("cursor must not be empty")
        }
        wire.cursor = cursor
    }
    return wire
}

function checkedDay(name: string, value: string): number {
    const day = typeof value === "string" && DATE.test(value) ? Date.parse(value) : Number.NaN
    if (Number.isNaN(day) || new Date(day).toISOString().slice(0, 10) !== value) {
        throw new InvalidRequestError(`${name} must be a date such as 2026-09-28`)
    }
    return day
}

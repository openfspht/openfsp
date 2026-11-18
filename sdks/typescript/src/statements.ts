import { InvalidRequestError } from "./errors.js"
import type { Api } from "./http/api.js"
import type { StatementPage, StatementQuery } from "./models/statement.js"
import { toStatementEntry } from "./protocol/mapping.js"
import { requireShape } from "./protocol/shape.js"
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

    /** Pass `nextCursor` back as `cursor`, with the same period, to read the following page. */
    async list(query: StatementQuery): Promise<StatementPage> {
        const found = await this.api.call<WireCollection<WireStatementEntry>>({
            method: "GET",
            path: "v1/statements",
            query: toWireQuery(query),
        })
        const entries = requireShape(found, ["data"]).data.map((entry) =>
            toStatementEntry(requireShape(entry, ENTRY_FIELDS))
        )
        return Object.freeze({
            entries: Object.freeze(entries),
            ...(found.next_cursor == null ? {} : { nextCursor: found.next_cursor }),
        })
    }
}

function toWireQuery(query: StatementQuery): Record<string, string> {
    const { provider, businessDate, from, to, limit, cursor } = query
    if (typeof provider !== "string" || provider.trim() === "") {
        throw new InvalidRequestError("provider must not be empty")
    }
    const wire: Record<string, string> = { provider, ...period(businessDate, from, to) }
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

function period(
    businessDate: string | undefined,
    from: string | undefined,
    to: string | undefined
): Record<string, string> {
    if (businessDate !== undefined && from === undefined && to === undefined) {
        checkedDay("businessDate", businessDate)
        return { business_date: businessDate }
    }
    if (businessDate === undefined && from !== undefined && to !== undefined) {
        const days = (checkedDay("to", to) - checkedDay("from", from)) / MILLISECONDS_PER_DAY + 1
        if (days < 1) {
            throw new InvalidRequestError("to must not precede from")
        }
        if (days > MAX_RANGE_DAYS) {
            throw new InvalidRequestError(`a range covers at most ${MAX_RANGE_DAYS} days`)
        }
        return { from, to }
    }
    throw new InvalidRequestError("a statement takes either businessDate, or from and to")
}

function checkedDay(name: string, value: string): number {
    const day = typeof value === "string" && DATE.test(value) ? Date.parse(value) : Number.NaN
    if (Number.isNaN(day) || new Date(day).toISOString().slice(0, 10) !== value) {
        throw new InvalidRequestError(`${name} must be a date such as 2026-09-28`)
    }
    return day
}

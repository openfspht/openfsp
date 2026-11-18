import { InvalidRequestError } from "./errors.js"
import type { Api } from "./http/api.js"
import type { Statement } from "./models/statement.js"
import { toStatement } from "./protocol/mapping.js"
import { requireShape } from "./protocol/shape.js"
import type { WireCollection, WireStatement } from "./protocol/wire.js"

const BUSINESS_DATE = /^\d{4}-\d{2}-\d{2}$/
const STATEMENT_FIELDS = [
    "id",
    "provider",
    "business_date",
    "complete",
    "entries",
    "missing_payments",
] as const

export class Statements {
    private readonly api: Api

    constructor(api: Api) {
        this.api = api
    }

    async find(provider: string, businessDate: string): Promise<Statement | undefined> {
        if (provider.trim() === "") {
            throw new InvalidRequestError("provider must not be empty")
        }
        if (!BUSINESS_DATE.test(businessDate)) {
            throw new InvalidRequestError("businessDate must be a date such as 2026-11-16")
        }
        const found = await this.api.call<WireCollection<WireStatement>>({
            method: "GET",
            path: "v1/statements",
            query: { provider, business_date: businessDate },
        })
        const first = requireShape(found, ["data"]).data[0]
        return first === undefined ? undefined : toStatement(requireShape(first, STATEMENT_FIELDS))
    }
}

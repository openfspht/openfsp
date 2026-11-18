import { ApiError, InvalidRequestError, TransportError } from "./errors.js"
import type { Api } from "./http/api.js"
import type { Payment } from "./models/payment.js"
import type { PaymentRequest } from "./models/payment-request.js"
import { toPayment, toWirePaymentRequest } from "./protocol/mapping.js"
import { requireShape } from "./protocol/shape.js"
import type { WireCollection, WirePayment } from "./protocol/wire.js"

const IDEMPOTENCY_KEY = /^[A-Za-z0-9_-]{1,255}$/
const PAYMENT_ID = /^[A-Za-z0-9_-]{1,64}$/
const PAYMENT_FIELDS = [
    "id",
    "reference",
    "status",
    "amount",
    "provider",
    "created_at",
    "updated_at",
] as const

const STATUSES: ReadonlySet<string> = new Set([
    "pending",
    "succeeded",
    "failed",
    "expired",
    "canceled",
])

export function newIdempotencyKey(): string {
    return crypto.randomUUID()
}

export interface PaymentsOptions {
    readonly recoverLostAnswers: boolean
    readonly pendingOnUnknownStatus: boolean
}

export class Payments {
    private readonly api: Api
    private readonly options: PaymentsOptions

    constructor(
        api: Api,
        options: PaymentsOptions = { recoverLostAnswers: true, pendingOnUnknownStatus: false }
    ) {
        this.api = api
        this.options = options
    }

    /** Persist the idempotency key before calling: resending with it after a crash cannot charge twice. */
    async create(
        request: PaymentRequest,
        options: { readonly idempotencyKey: string }
    ): Promise<Payment> {
        const body = toWirePaymentRequest(request)
        const idempotencyKey: unknown = options?.idempotencyKey
        if (typeof idempotencyKey !== "string" || !IDEMPOTENCY_KEY.test(idempotencyKey)) {
            throw new InvalidRequestError(
                "idempotencyKey must hold 1 to 255 characters from A-Za-z0-9_-"
            )
        }
        try {
            return this.toPayment(
                await this.api.call<WirePayment>({
                    method: "POST",
                    path: "v1/payments",
                    body,
                    idempotencyKey,
                })
            )
        } catch (failure) {
            if (!this.options.recoverLostAnswers || !hasUnknownEffect(failure)) {
                throw failure
            }
            const recovered = await this.findByReference(request.reference).catch(() => undefined)
            if (recovered?.matches(request.reference, request.amount, request.provider)) {
                return recovered
            }
            throw failure
        }
    }

    async get(id: string): Promise<Payment> {
        return this.read("GET", `v1/payments/${checkedId(id)}`)
    }

    async synchronize(id: string): Promise<Payment> {
        return this.read("POST", `v1/payments/${checkedId(id)}/synchronize`)
    }

    async findByReference(reference: string): Promise<Payment | undefined> {
        if (reference.trim() === "") {
            throw new InvalidRequestError("reference must not be empty")
        }
        const found = await this.api.call<WireCollection<WirePayment>>({
            method: "GET",
            path: "v1/payments",
            query: { reference },
        })
        const first = requireShape(found, ["data"]).data[0]
        return first === undefined ? undefined : this.toPayment(first)
    }

    private async read(method: "GET" | "POST", path: string): Promise<Payment> {
        return this.toPayment(await this.api.call<WirePayment>({ method, path }))
    }

    private toPayment(wire: WirePayment): Payment {
        const checked = requireShape(wire, PAYMENT_FIELDS)
        if (STATUSES.has(checked.status)) {
            return toPayment(checked)
        }
        if (this.options.pendingOnUnknownStatus) {
            return toPayment(checked, "pending")
        }
        throw new ApiError({
            status: 200,
            code: "unexpected-response",
            title: `unknown payment status ${String(checked.status)}`,
            retryable: false,
            effect: "unknown",
        })
    }
}

function hasUnknownEffect(failure: unknown): boolean {
    return (
        failure instanceof TransportError ||
        (failure instanceof ApiError && failure.effect === "unknown")
    )
}

function checkedId(id: string): string {
    if (!PAYMENT_ID.test(id)) {
        throw new InvalidRequestError("payment id must hold 1 to 64 characters from A-Za-z0-9_-")
    }
    return id
}

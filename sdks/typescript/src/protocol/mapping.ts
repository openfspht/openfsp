import { InvalidRequestError } from "../errors.js"
import type { ServerConfiguration } from "../models/configuration.js"
import { PaymentEvent, type PaymentEventType } from "../models/event.js"
import { Money } from "../models/money.js"
import { Payment, type FailureReason, type NextAction } from "../models/payment.js"
import type { PaymentRequest } from "../models/payment-request.js"
import { Provider } from "../models/provider.js"
import type {
    WireConfiguration,
    WireEvent,
    WireMoney,
    WireNextAction,
    WirePayment,
    WirePaymentRequest,
    WireProvider,
} from "./wire.js"

const PHONE_NUMBER = /^\+[1-9]\d{1,14}$/

export function toWirePaymentRequest(request: PaymentRequest): WirePaymentRequest {
    if (request.reference.trim() === "") {
        throw new InvalidRequestError("reference must not be empty")
    }
    if (request.provider.trim() === "") {
        throw new InvalidRequestError("provider must not be empty")
    }
    if (!request.amount.isPositive) {
        throw new InvalidRequestError("amount must be positive")
    }
    if (request.payerPhoneNumber !== undefined && !PHONE_NUMBER.test(request.payerPhoneNumber)) {
        throw new InvalidRequestError(
            "payerPhoneNumber must be in E.164 format, for example +50934567890"
        )
    }
    return withoutUndefined({
        reference: request.reference,
        amount: request.amount.toJSON(),
        provider: request.provider,
        payer:
            request.payerPhoneNumber === undefined
                ? undefined
                : { phone_number: request.payerPhoneNumber },
        description: request.description,
        return_url: request.returnUrl,
        callback_url: request.callbackUrl,
        expires_at: request.expiresAt?.toISOString(),
        locale: request.locale,
        metadata: request.metadata === undefined ? undefined : { ...request.metadata },
    })
}

export function toPayment(wire: WirePayment): Payment {
    return new Payment({
        id: wire.id,
        reference: wire.reference,
        status: wire.status,
        amount: toMoney(wire.amount),
        provider: wire.provider,
        payerPhoneNumber: wire.payer?.phone_number,
        description: wire.description ?? undefined,
        nextAction: toNextAction(wire.next_action),
        createdAt: new Date(wire.created_at),
        updatedAt: new Date(wire.updated_at),
        expiresAt: toDate(wire.expires_at),
        completedAt: toDate(wire.completed_at),
        failureReason: (wire.failure_reason ?? undefined) as FailureReason | undefined,
        fee: wire.fee ? { amount: toMoney(wire.fee.amount), bearer: wire.fee.bearer } : undefined,
        metadata: Object.freeze({ ...wire.metadata }),
    })
}

export function toProvider(wire: WireProvider): Provider {
    const limits = wire.payments.limits ?? {}
    return new Provider({
        id: wire.id,
        displayName: wire.display_name,
        logoUrl: wire.logo_url ?? undefined,
        capabilities: Object.freeze([...wire.capabilities]),
        nextActions: Object.freeze([...wire.payments.next_actions]),
        payerRequired: wire.payments.payer_required,
        returnUrlRequired: wire.payments.return_url_required,
        expiryGuaranteed: wire.payments.expiry_guaranteed,
        limits: Object.freeze({
            min: optionalMoney(limits.min_amount),
            max: optionalMoney(limits.max_amount),
            step: optionalMoney(limits.amount_step),
        }),
        locales: Object.freeze([...wire.payments.locales]),
    })
}

export function toConfiguration(wire: WireConfiguration): ServerConfiguration {
    return Object.freeze({
        issuer: wire.issuer,
        protocolVersion: wire.protocol_version,
        capabilitiesSupported: Object.freeze([...wire.capabilities_supported]),
        apiBase: wire.api_base,
        paymentsEndpoint: wire.payments_endpoint,
        capabilitiesEndpoint: wire.capabilities_endpoint,
        statementsEndpoint: wire.statements_endpoint,
        statementEntriesEndpoint: wire.statement_entries_endpoint,
        webhookKeys: Object.freeze(
            (wire.webhook_keys ?? []).map((key) => Object.freeze({ ...key }))
        ),
        authenticationMethodsSupported: Object.freeze([
            ...(wire.authentication_methods_supported ?? []),
        ]),
        scopesSupported: Object.freeze([...(wire.scopes_supported ?? [])]),
        webhookSignatureAlgorithmsSupported: Object.freeze([
            ...(wire.webhook_signature_algorithms_supported ?? []),
        ]),
    })
}

export function toPaymentEvent(wire: WireEvent): PaymentEvent {
    return new PaymentEvent({
        id: wire.id,
        type: wire.type as PaymentEventType,
        sequence: wire.sequence,
        createdAt: new Date(wire.created_at),
        previousStatus: wire.previous_status,
        payment: toPayment(wire.data),
    })
}

export function toMoney(wire: WireMoney): Money {
    return Money.centimes(wire.amount)
}

function optionalMoney(wire: WireMoney | undefined): Money | undefined {
    return wire === undefined ? undefined : toMoney(wire)
}

function toNextAction(wire: WireNextAction | null | undefined): NextAction {
    if (wire?.type === "redirect" && wire.redirect_url !== undefined) {
        return Object.freeze({
            type: "redirect",
            url: wire.redirect_url,
            expiresAt: toDate(wire.expires_at),
        })
    }
    if (wire?.type === "payer_approval") {
        return Object.freeze({ type: "payer_approval" })
    }
    return Object.freeze({ type: "none" })
}

function toDate(value: string | null | undefined): Date | undefined {
    return value ? new Date(value) : undefined
}

function withoutUndefined<T extends object>(value: T): T {
    return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined)) as T
}

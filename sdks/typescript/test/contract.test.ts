import { describe, expect, test } from "bun:test"
import type {
    WireCapabilities as Capabilities,
    WireConfiguration as Configuration,
    WireEvent as PaymentEvent,
    WireFee as Fee,
    WireMoney as Money,
    WirePayer as Payer,
    WirePayment as Payment,
    WirePaymentRequest as PaymentRequest,
    WireProblem as Problem,
    WireProvider as ProviderCapabilities,
} from "../src/protocol/wire"

type Fields<T> = Record<keyof T, true>

const SCHEMAS = new URL("../../../conformance/schemas/", import.meta.url)

async function schema(name: string): Promise<Record<string, any>> {
    return Bun.file(new URL(name, SCHEMAS)).json()
}

function keys(fields: object): string[] {
    return Object.keys(fields).sort()
}

const payment = {
    id: true,
    reference: true,
    status: true,
    amount: true,
    provider: true,
    payer: true,
    description: true,
    next_action: true,
    created_at: true,
    updated_at: true,
    expires_at: true,
    completed_at: true,
    failure_reason: true,
    failure_detail: true,
    fee: true,
    metadata: true,
} satisfies Fields<Payment>
const paymentRequest = {
    reference: true,
    amount: true,
    provider: true,
    payer: true,
    description: true,
    return_url: true,
    expires_at: true,
    locale: true,
    metadata: true,
    callback_url: true,
} satisfies Fields<PaymentRequest>
const problem = {
    type: true,
    title: true,
    status: true,
    detail: true,
    instance: true,
    retryable: true,
    effect: true,
    request_id: true,
    errors: true,
    min_amount: true,
    max_amount: true,
    amount_step: true,
} satisfies Fields<Problem>
const event = {
    id: true,
    type: true,
    created_at: true,
    sequence: true,
    resource_type: true,
    resource_id: true,
    reference: true,
    previous_status: true,
    data: true,
} satisfies Fields<PaymentEvent>
const configuration = {
    issuer: true,
    protocol_version: true,
    api_base: true,
    payments_endpoint: true,
    capabilities_endpoint: true,
    statements_endpoint: true,
    webhook_keys: true,
    authentication_methods_supported: true,
    scopes_supported: true,
    capabilities_supported: true,
    webhook_signature_algorithms_supported: true,
    conformance_endpoint: true,
} satisfies Fields<Configuration>
const capabilities = { protocol_version: true, providers: true } satisfies Fields<Capabilities>
const provider = {
    id: true,
    display_name: true,
    logo_url: true,
    capabilities: true,
    payments: true,
} satisfies Fields<ProviderCapabilities>
const providerPayments = {
    next_actions: true,
    payer_required: true,
    return_url_required: true,
    expiry_guaranteed: true,
    limits: true,
    locales: true,
} satisfies Fields<ProviderCapabilities["payments"]>
const money = { amount: true, currency: true } satisfies Fields<Money>
const fee = { amount: true, bearer: true } satisfies Fields<Fee>
const payer = { phone_number: true } satisfies Fields<Payer>

describe("wire types follow the conformance schemas", () => {
    test.each([
        ["payment.json", payment],
        ["payment-request.json", paymentRequest],
        ["problem.json", problem],
        ["event.json", event],
        ["discovery.json", configuration],
        ["capabilities.json", capabilities],
    ] as const)("%s", async (name, fields) => {
        expect(keys(fields)).toEqual(Object.keys((await schema(name)).properties).sort())
    })

    test("nested shapes", async () => {
        const common = (await schema("common.json")).$defs
        const providers = (await schema("capabilities.json")).properties.providers.items
        expect(keys(money)).toEqual(Object.keys(common.money.properties).sort())
        expect(keys(fee)).toEqual(Object.keys(common.fee.properties).sort())
        expect(keys(payer)).toEqual(Object.keys(common.payer.properties).sort())
        expect(keys(provider)).toEqual(Object.keys(providers.properties).sort())
        expect(keys(providerPayments)).toEqual(
            Object.keys(providers.properties.payments.properties).sort()
        )
    })

    test("required fields are not optional", async () => {
        const required = (await schema("payment.json")).required as (keyof Payment)[]
        const sample: Payment = {
            id: "",
            reference: "",
            status: "pending",
            amount: { amount: 0, currency: "HTG" },
            provider: "",
            created_at: "",
            updated_at: "",
        }
        expect(keys(sample)).toEqual([...required].sort())
    })
})

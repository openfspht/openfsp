export interface WireMoney {
    amount: number
    currency: "HTG"
}

export interface WirePayer {
    phone_number: string
}

export interface WireFee {
    amount: WireMoney
    bearer: "merchant" | "payer"
}

export interface WireNextAction {
    type: "redirect" | "payer_approval" | "none"
    redirect_url?: string
    expires_at?: string | null
}

export interface WirePaymentRequest {
    reference: string
    amount: WireMoney
    provider: string
    payer?: WirePayer | null
    description?: string | null
    return_url?: string | null
    expires_at?: string | null
    locale?: "fr" | "ht" | "en" | null
    metadata?: Record<string, string> | null
    callback_url?: string | null
}

export interface WirePayment {
    id: string
    reference: string
    status: "pending" | "succeeded" | "failed" | "expired" | "canceled"
    amount: WireMoney
    provider: string
    payer?: WirePayer | null
    description?: string | null
    next_action?: WireNextAction | null
    created_at: string
    updated_at: string
    expires_at?: string | null
    completed_at?: string | null
    failure_reason?: string | null
    failure_detail?: Record<string, unknown> | null
    fee?: WireFee | null
    metadata?: Record<string, string> | null
}

export interface WireCollection<T> {
    data: T[]
}

export interface WireEvent {
    id: string
    type: string
    created_at: string
    sequence: number
    resource_type: "payment"
    resource_id: string
    reference: string
    previous_status?: WirePayment["status"]
    data: WirePayment
}

export interface WireLimits {
    min_amount?: WireMoney
    max_amount?: WireMoney
    amount_step?: WireMoney
}

export interface WireProvider {
    id: string
    display_name: string
    logo_url?: string | null
    capabilities: string[]
    payments: {
        next_actions: WireNextAction["type"][]
        payer_required: boolean
        return_url_required: boolean
        expiry_guaranteed: boolean
        limits?: WireLimits | null
        locales: ("fr" | "ht" | "en")[]
    }
}

export interface WireCapabilities {
    protocol_version: string
    providers: WireProvider[]
}

export interface WireJwk {
    kid: string
    kty: string
    crv?: string
    alg?: string
    use?: string
    x?: string
    openfsp_status?: "active" | "retired"
}

export interface WireConfiguration {
    issuer: string
    protocol_version: string
    api_base?: string
    payments_endpoint?: string
    capabilities_endpoint?: string
    statements_endpoint?: string
    statement_entries_endpoint?: string
    webhook_keys?: WireJwk[]
    authentication_methods_supported?: string[]
    scopes_supported?: string[]
    capabilities_supported: string[]
    webhook_signature_algorithms_supported?: string[]
}

export interface WireFieldError {
    field: string
    code: string
    detail?: string | null
}

export interface WireProblem {
    type: string
    title: string
    status: number
    detail?: string | null
    instance?: string | null
    retryable: boolean
    effect: "none" | "unknown"
    request_id: string
    errors?: WireFieldError[] | null
    min_amount?: WireMoney
    max_amount?: WireMoney
    amount_step?: WireMoney
}

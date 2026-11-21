import type { EventType, Fee, Money, Payment, PaymentStatus, Timestamp } from "./openfsp"

export type TransitionSource =
    "creation" | "lookup" | "signed_callback" | "per_payment_url" | "statement" | "attestation"

export interface Transition {
    from_status?: PaymentStatus
    to_status: PaymentStatus
    source: TransitionSource
    recorded_at: Timestamp
}

export interface Conflict {
    source: TransitionSource
    reported: { status: PaymentStatus; amount: number }
    recorded_at: Timestamp
}

export interface DeliveryAttempt {
    attempt: number
    started_at: Timestamp
    status_code?: number
    error?: string
    duration_ms: number
}

export type DeliveryStatus = "pending" | "succeeded" | "abandoned" | "stopped"

export interface Delivery {
    endpoint: string
    url: string
    status: DeliveryStatus
    attempts: DeliveryAttempt[]
    next_attempt_at?: Timestamp
}

export interface EventView {
    id: string
    type: EventType
    sequence: number
    created_at: Timestamp
    deliveries: Delivery[]
}

export interface PaymentDetail {
    payment: Payment
    automatic_sources: boolean
    indeterminate: string[]
    transitions: Transition[]
    conflicts: Conflict[]
    events: EventView[]
}

export type AttestedStatus = "succeeded" | "failed" | "expired" | "canceled"

export interface AttestationRequest {
    person: string
    status: AttestedStatus
    evidence: string
    failure_reason?: string
}

export interface StatementEntry {
    provider: string
    business_date: string
    booked_at: Timestamp
    amount: Money
    fee?: Fee
    payment?: string
    reference?: string
}

export interface StatementPage {
    data: StatementEntry[]
    next_cursor?: string
}

export const SCOPES = [
    "capabilities:read",
    "payments:read",
    "payments:write",
    "statements:read",
] as const

export type ScopeName = (typeof SCOPES)[number]

export interface MerchantAccount {
    id: string
    name: string
    created_at: Timestamp
    expires_at?: Timestamp
}

export interface AccountProvider {
    provider: string
    display_name: string
    mode: CredentialMode
    source: "account" | "platform" | "none"
    accepts_account_credentials: boolean
    credential_names: string[]
    updated_at?: Timestamp
    notification_url?: string
    return_url?: string
    revisions: CredentialRevision[]
}

export type CredentialMode = "platform" | "own"

export type CredentialStatus = "active" | "retired" | "revoked"

export interface CredentialRevision {
    id: string
    status: CredentialStatus
    sealed: boolean
    created_at: Timestamp
    retired_at?: Timestamp
    revoked_at?: Timestamp
}

export interface AccountProviders {
    data: AccountProvider[]
}

export interface ApiKeySummary {
    id: string
    account: string
    principal: string
    prefix: string
    scopes: ScopeName[]
    created_at: Timestamp
    expires_at?: Timestamp
    last_used_at?: Timestamp
    revoked_at?: Timestamp
    demo_only: boolean
}

export interface IssuedKey {
    id: string
    secret: string
}

export interface ProviderSummary {
    id: string
    display_name: string
    logo_url: string | null
    capabilities: string[]
}

export const ACTIVITY_KINDS = ["transition", "conflict", "delivery", "key", "admin"] as const

export type ActivityKind = (typeof ACTIVITY_KINDS)[number]

export interface ActivityPayment {
    id: string
    reference: string
    provider: string
    amount: Money
    status: PaymentStatus
}

export interface ActivityItem {
    id: string
    at: Timestamp
    kind: ActivityKind
    source?: string
    payment?: ActivityPayment
    from_status?: PaymentStatus
    to_status?: PaymentStatus
    event_type?: EventType
    endpoint?: string
    status_code?: number
    attempt?: number
    duration_ms?: number
    subject?: string
    result?: string
}

export interface StatusTotal {
    status: PaymentStatus
    count: number
    amount: Money
}

export interface Overview {
    environment: string
    business_date: string
    payments: StatusTotal[]
    retrying_deliveries: number
    disabled_endpoints: number
    active_keys: number
}

export interface AuditRecord {
    recorded_at: Timestamp
    kind: string
    principal_id?: string
    lookup_prefix?: string
    operation: string
    request_id?: string
    result: string
    detail?: string
    exchange?: HttpExchange
}

export interface HttpExchange {
    remote_address: string
    duration_ms: number
    request_headers: Record<string, string>
    request_body: string
    response_headers: Record<string, string>
    response_body: string
}

export interface WebhookEndpoint {
    account: string
    name: string
    url: string
    events: EventType[]
    enabled: boolean
    managed: boolean
    pending: number
    disabled_at?: Timestamp
    reason?: string
}

export interface SigningKeyView {
    kid: string
    status: "active" | "retired"
    activates_at: Timestamp
    retired_at?: Timestamp
}

export interface SigningKeys {
    keys: SigningKeyView[]
    rotate_every_minutes?: number
    next_rotation_at?: Timestamp
}

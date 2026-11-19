import type {
    CredentialMode,
    AccountProviders,
    ActivityItem,
    ApiKeySummary,
    AuditRecord,
    AttestationRequest,
    Collection,
    EventType,
    IssuedKey,
    MerchantAccount,
    Overview,
    Page,
    Payment,
    PaymentDetail,
    ProviderSummary,
    ScopeName,
    SigningKeys,
    StatementPage,
    WebhookEndpoint,
} from "@workspace/types"

import { download, post, put, query, remove, request, type QueryParams } from "@workspace/http"

const ADMIN = "/gateway/admin"

export const gatewayAdmin = {
    accounts: () => request<Collection<MerchantAccount>>(`${ADMIN}/accounts`),
    openAccount: (name: string) => request<MerchantAccount>(`${ADMIN}/accounts`, post({ name })),
    accountProviders: (account: string) =>
        request<AccountProviders>(`${ADMIN}/accounts/${encodeURIComponent(account)}/providers`),
    saveCredentials: (account: string, provider: string, credentials: Record<string, string>) =>
        request<void>(
            `${ADMIN}/accounts/${encodeURIComponent(account)}/providers/${encodeURIComponent(provider)}/credentials`,
            put({ credentials })
        ),
    removeCredentials: (account: string, provider: string) =>
        request<void>(
            `${ADMIN}/accounts/${encodeURIComponent(account)}/providers/${encodeURIComponent(provider)}/credentials`,
            remove()
        ),
    switchMode: (account: string, provider: string, mode: CredentialMode) =>
        request<void>(
            `${ADMIN}/accounts/${encodeURIComponent(account)}/providers/${encodeURIComponent(provider)}/mode`,
            put({ mode })
        ),
    revokeRevision: (account: string, provider: string, revision: string) =>
        request<void>(
            `${ADMIN}/accounts/${encodeURIComponent(account)}/providers/${encodeURIComponent(provider)}/revisions/${encodeURIComponent(revision)}/revoke`,
            post()
        ),
    configuration: () =>
        request<{
            configuration: unknown
            capabilities: unknown
            signing: SigningKeys
        }>(`${ADMIN}/configuration`),
    overview: () => request<Overview>(`${ADMIN}/overview`),
    activity: (params: QueryParams) =>
        request<Page<ActivityItem>>(`${ADMIN}/activity?${query(params)}`),
    activityExport: (params: QueryParams) =>
        request<Page<ActivityItem>>(`${ADMIN}/activity/export?${query(params)}`),
    auditExport: (params: QueryParams) =>
        request<Page<AuditRecord>>(`${ADMIN}/audit/export?${query(params)}`),
    paymentsExport: (params: QueryParams) =>
        request<Page<Payment>>(`${ADMIN}/payments/export?${query(params)}`),
    audit: (params: QueryParams) => request<Page<AuditRecord>>(`${ADMIN}/audit?${query(params)}`),
    webhookEndpoints: (params: QueryParams) =>
        request<Page<WebhookEndpoint>>(`${ADMIN}/webhooks/endpoints?${query(params)}`),
    addEndpoint: (webhook: { account: string; name: string; url: string; events: EventType[] }) =>
        request<void>(`${ADMIN}/webhooks/endpoints`, post(webhook)),
    removeEndpoint: (name: string) =>
        request<void>(`${ADMIN}/webhooks/endpoints/${encodeURIComponent(name)}`, remove()),
    enableEndpoint: (name: string) =>
        request<void>(`${ADMIN}/webhooks/endpoints/${encodeURIComponent(name)}/enable`, post()),
    rotateSigningKey: () => request<{ kid: string }>(`${ADMIN}/webhooks/keys/rotate`, post()),
    providers: () => request<Collection<ProviderSummary>>(`${ADMIN}/providers`),
    payments: (params: QueryParams) => request<Page<Payment>>(`${ADMIN}/payments?${query(params)}`),
    payment: (id: string) => request<PaymentDetail>(`${ADMIN}/payments/${encodeURIComponent(id)}`),
    attest: (id: string, attestation: AttestationRequest) =>
        request<PaymentDetail>(
            `${ADMIN}/payments/${encodeURIComponent(id)}/attestations`,
            post(attestation)
        ),
    statements: (params: QueryParams) =>
        request<StatementPage>(`${ADMIN}/statements?${query(params)}`),
    exportStatements: (params: QueryParams, filename: string) =>
        download(`${ADMIN}/statements/export?${query(params)}`, filename),
    keys: (params: QueryParams) => request<Page<ApiKeySummary>>(`${ADMIN}/keys?${query(params)}`),
    issueKey: (account: string, principal: string, scopes: ScopeName[]) =>
        request<IssuedKey>(`${ADMIN}/keys`, post({ account, principal, scopes })),
    revokeKey: (id: string) =>
        request<void>(`${ADMIN}/keys/${encodeURIComponent(id)}/revoke`, post()),
}

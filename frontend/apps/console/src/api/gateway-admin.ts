import type {
  ActivityItem,
  ActivityKind,
  ApiKeySummary,
  AuditRecord,
  AttestationRequest,
  Collection,
  GatewayStatement,
  IssuedKey,
  Overview,
  Payment,
  PaymentDetail,
  ProviderSummary,
  ScopeName,
  WebhookEndpoint,
} from "@workspace/types"

import { post, request } from "./http"

const ADMIN = "/gateway/admin"

export const gatewayAdmin = {
  overview: () => request<Overview>(`${ADMIN}/overview`),
  activity: (kinds: readonly ActivityKind[]) =>
    request<Collection<ActivityItem>>(`${ADMIN}/activity?limit=150${kinds.map((kind) => `&kind=${kind}`).join("")}`),
  audit: () => request<Collection<AuditRecord>>(`${ADMIN}/audit?limit=200`),
  webhookEndpoints: () => request<Collection<WebhookEndpoint>>(`${ADMIN}/webhooks/endpoints`),
  enableEndpoint: (name: string) => request<void>(`${ADMIN}/webhooks/endpoints/${encodeURIComponent(name)}/enable`, post()),
  rotateSigningKey: () => request<{ kid: string }>(`${ADMIN}/webhooks/keys/rotate`, post()),
  providers: () => request<Collection<ProviderSummary>>(`${ADMIN}/providers`),
  payments: () => request<Collection<Payment>>(`${ADMIN}/payments`),
  payment: (id: string) => request<PaymentDetail>(`${ADMIN}/payments/${id}`),
  attest: (id: string, attestation: AttestationRequest) =>
    request<PaymentDetail>(
      `${ADMIN}/payments/${id}/attestations`,
      post(attestation)
    ),
  gatewayStatement: (provider: string) =>
    request<Collection<GatewayStatement>>(
      `${ADMIN}/gateway_statements?provider=${provider}`
    ),
  keys: () => request<Collection<ApiKeySummary>>(`${ADMIN}/keys`),
  issueKey: (principal: string, scopes: ScopeName[]) =>
    request<IssuedKey>(`${ADMIN}/keys`, post({ principal, scopes })),
  revokeKey: (id: string) =>
    request<void>(`${ADMIN}/keys/${id}/revoke`, post()),
}

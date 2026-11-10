import type {
  ActivityItem,
  ActivityKind,
  ApiKeySummary,
  AuditRecord,
  AttestationRequest,
  Collection,
  EventType,
  GatewayStatement,
  IssuedKey,
  Overview,
  Page,
  Payment,
  PaymentDetail,
  ProviderSummary,
  ScopeName,
  SigningKeys,
  WebhookEndpoint,
} from "@workspace/types"

import { post, remove, request } from "./http"

const ADMIN = "/gateway/admin"

const query = (params: Record<string, string | number>) =>
  new URLSearchParams(Object.entries(params).filter(([, value]) => value !== "").map(([key, value]) => [key, String(value)])).toString()

export const gatewayAdmin = {
  configuration: () => request<{ configuration: unknown; capabilities: unknown; signing: SigningKeys }>(`${ADMIN}/configuration`),
  overview: () => request<Overview>(`${ADMIN}/overview`),
  activity: (kinds: readonly ActivityKind[]) =>
    request<Collection<ActivityItem>>(`${ADMIN}/activity?limit=150${kinds.map((kind) => `&kind=${kind}`).join("")}`),
  audit: (params: Record<string, string | number>) => request<Page<AuditRecord>>(`${ADMIN}/audit?${query(params)}`),
  webhookEndpoints: () => request<Collection<WebhookEndpoint>>(`${ADMIN}/webhooks/endpoints`),
  addEndpoint: (webhook: { name: string; url: string; events: EventType[] }) => request<void>(`${ADMIN}/webhooks/endpoints`, post(webhook)),
  removeEndpoint: (name: string) => request<void>(`${ADMIN}/webhooks/endpoints/${encodeURIComponent(name)}`, remove()),
  enableEndpoint: (name: string) => request<void>(`${ADMIN}/webhooks/endpoints/${encodeURIComponent(name)}/enable`, post()),
  rotateSigningKey: () => request<{ kid: string }>(`${ADMIN}/webhooks/keys/rotate`, post()),
  providers: () => request<Collection<ProviderSummary>>(`${ADMIN}/providers`),
  payments: (params: Record<string, string | number>) => request<Page<Payment>>(`${ADMIN}/payments?${query(params)}`),
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

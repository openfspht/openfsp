import type {
  ApiKeySummary,
  AttestationRequest,
  Collection,
  GatewayStatement,
  IssuedKey,
  Payment,
  PaymentDetail,
  ProviderSummary,
  ScopeName,
} from "@workspace/types"

import { post, request } from "./http"

const ADMIN = "/gateway/admin"

export const gatewayAdmin = {
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

import type { AttestationRequest, Collection, Outcome, Payment, PaymentDetail, Scenario, SimulatedOperator, SimulatedPayment } from "@workspace/types"

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  })
  if (!response.ok) throw new Error(`${init?.method ?? "GET"} ${path} answered ${response.status}`)
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const gateway = {
  payments: () => request<Collection<Payment>>("/gateway/admin/payments"),
  payment: (id: string) => request<PaymentDetail>(`/gateway/admin/payments/${id}`),
  attest: (id: string, attestation: AttestationRequest) =>
    request<PaymentDetail>(`/gateway/admin/payments/${id}/attestations`, { method: "POST", body: JSON.stringify(attestation) }),
}

export const simulator = {
  payments: () => request<Collection<SimulatedPayment>>("/simulator/control/payments"),
  defaultScenario: (operator: SimulatedOperator) =>
    request<{ scenario: Scenario }>(`/simulator/control/operators/${operator}/default-scenario`),
  setDefaultScenario: (operator: SimulatedOperator, scenario: Scenario) =>
    request<void>(`/simulator/control/operators/${operator}/default-scenario`, {
      method: "PUT",
      body: JSON.stringify({ scenario }),
    }),
  resolve: (paymentId: string, outcome: Outcome, reason?: string) =>
    request<SimulatedPayment>(`/simulator/control/payments/${paymentId}/resolve`, {
      method: "POST",
      body: JSON.stringify({ outcome, reason }),
    }),
}

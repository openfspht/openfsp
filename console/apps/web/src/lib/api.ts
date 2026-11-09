import type { Collection, Outcome, Payment, PaymentDetail, Scenario, SimulatedPayment } from "@workspace/types"

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  })
  if (!response.ok) throw new Error(`${init?.method ?? "GET"} ${path} answered ${response.status}`)
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const SIMULATED_OPERATOR = "mock_alpha"

export const gateway = {
  payments: () => request<Collection<Payment>>("/gateway/admin/payments"),
  payment: (id: string) => request<PaymentDetail>(`/gateway/admin/payments/${id}`),
}

export const simulator = {
  payments: () => request<Collection<SimulatedPayment>>("/simulator/control/payments"),
  defaultScenario: () =>
    request<{ scenario: Scenario }>(`/simulator/control/operators/${SIMULATED_OPERATOR}/default-scenario`),
  setDefaultScenario: (scenario: Scenario) =>
    request<void>(`/simulator/control/operators/${SIMULATED_OPERATOR}/default-scenario`, {
      method: "PUT",
      body: JSON.stringify({ scenario }),
    }),
  resolve: (paymentId: string, outcome: Outcome, reason?: string) =>
    request<SimulatedPayment>(`/simulator/control/payments/${paymentId}/resolve`, {
      method: "POST",
      body: JSON.stringify({ outcome, reason }),
    }),
}

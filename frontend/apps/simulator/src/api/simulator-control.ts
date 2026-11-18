import type {
    Collection,
    JournalEntry,
    Outcome,
    Page,
    Scenario,
    SimulatedOperator,
    SimulatedPayment,
} from "@workspace/types"

import { post, put, query, request } from "@workspace/http"

import type { QueryParams } from "./query-keys"

const CONTROL = "/simulator/control"

export const simulatorControl = {
    payments: (params: QueryParams) =>
        request<Page<SimulatedPayment>>(`${CONTROL}/payments?${query(params)}`),
    journal: (params: QueryParams) =>
        request<Page<JournalEntry>>(`${CONTROL}/journal?${query(params)}`),
    scenarios: (operator: SimulatedOperator) =>
        request<Collection<Scenario>>(
            `${CONTROL}/operators/${encodeURIComponent(operator)}/scenarios`
        ),
    defaultScenario: (operator: SimulatedOperator) =>
        request<{ scenario: Scenario; automatic: boolean }>(
            `${CONTROL}/operators/${encodeURIComponent(operator)}/default-scenario`
        ),
    setAutomatic: (operator: SimulatedOperator, enabled: boolean) =>
        request<void>(
            `${CONTROL}/operators/${encodeURIComponent(operator)}/automatic`,
            put({ enabled })
        ),
    setDefaultScenario: (operator: SimulatedOperator, scenario: Scenario) =>
        request<void>(
            `${CONTROL}/operators/${encodeURIComponent(operator)}/default-scenario`,
            put({ scenario })
        ),
    resolve: (paymentId: string, outcome: Outcome, reason?: string) =>
        request<SimulatedPayment>(
            `${CONTROL}/payments/${encodeURIComponent(paymentId)}/resolve`,
            post({ outcome, reason })
        ),
    deliverCallback: (paymentId: string) =>
        request<SimulatedPayment>(
            `${CONTROL}/payments/${encodeURIComponent(paymentId)}/callback`,
            post()
        ),
    reset: () => request<void>(`${CONTROL}/reset`, post()),
}

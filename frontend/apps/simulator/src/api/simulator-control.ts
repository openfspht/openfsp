import type {
    Collection,
    JournalEntry,
    Outcome,
    Page,
    Scenario,
    SimulatedOperator,
    SimulatedPayment,
    StatementDays,
} from "@workspace/types"

import { post, put, request } from "./http"
import type { QueryParams } from "./query-keys"

const CONTROL = "/simulator/control"

const query = (params: QueryParams) =>
    new URLSearchParams(
        Object.entries(params).flatMap(([key, value]) =>
            value === "" ? [] : [[key, String(value)]]
        )
    ).toString()

export const simulatorControl = {
    payments: (params: QueryParams) =>
        request<Page<SimulatedPayment>>(`${CONTROL}/payments?${query(params)}`),
    journal: (params: QueryParams) =>
        request<Page<JournalEntry>>(`${CONTROL}/journal?${query(params)}`),
    scenarios: (operator: SimulatedOperator) =>
        request<Collection<Scenario>>(`${CONTROL}/operators/${operator}/scenarios`),
    defaultScenario: (operator: SimulatedOperator) =>
        request<{ scenario: Scenario; automatic: boolean }>(
            `${CONTROL}/operators/${operator}/default-scenario`
        ),
    setAutomatic: (operator: SimulatedOperator, enabled: boolean) =>
        request<void>(`${CONTROL}/operators/${operator}/automatic`, put({ enabled })),
    setDefaultScenario: (operator: SimulatedOperator, scenario: Scenario) =>
        request<void>(`${CONTROL}/operators/${operator}/default-scenario`, put({ scenario })),
    resolve: (paymentId: string, outcome: Outcome, reason?: string) =>
        request<SimulatedPayment>(
            `${CONTROL}/payments/${paymentId}/resolve`,
            post({ outcome, reason })
        ),
    deliverCallback: (paymentId: string) =>
        request<SimulatedPayment>(`${CONTROL}/payments/${paymentId}/callback`, post()),
    statementDays: (operator: SimulatedOperator) =>
        request<StatementDays>(`${CONTROL}/operators/${operator}/statements`),
    closeDay: (operator: SimulatedOperator, day: string) =>
        request<void>(`${CONTROL}/operators/${operator}/statements/${day}/close`, post()),
    reset: () => request<void>(`${CONTROL}/reset`, post()),
}

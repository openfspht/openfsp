import { SIMULATED_OPERATORS, type SimulatedOperator } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

const isOperator = (value: string): value is SimulatedOperator =>
    (SIMULATED_OPERATORS as readonly string[]).includes(value)

export function useOperators() {
    const { t } = useI18n()
    const names: Record<string, string> = t.operators
    const nameOf = (operator: string) => names[operator] ?? operator
    const pick = (value: string): SimulatedOperator =>
        isOperator(value) ? value : SIMULATED_OPERATORS[0]
    return { operators: SIMULATED_OPERATORS, names, nameOf, pick }
}

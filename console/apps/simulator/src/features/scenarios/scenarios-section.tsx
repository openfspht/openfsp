import { SimulatorPanel } from "@workspace/design-system"
import type { SimulatedOperator, SimulatedPayment } from "@workspace/types"

import { useScenarios } from "./use-scenarios"

interface ScenariosSectionProps {
  operator: SimulatedOperator
  onOperatorChange: (operator: SimulatedOperator) => void
  payments: SimulatedPayment[]
  unreachable: boolean
}

export function ScenariosSection({
  operator,
  onOperatorChange,
  payments,
  unreachable,
}: ScenariosSectionProps) {
  const scenarios = useScenarios(operator)
  return (
    <SimulatorPanel
      operator={operator}
      onOperatorChange={onOperatorChange}
      scenario={scenarios.scenario.data?.scenario ?? "SUCCESS"}
      onScenarioChange={scenarios.setScenario.mutate}
      waiting={payments.filter((payment) => !payment.outcome)}
      onResolve={(id, resolution) =>
        scenarios.resolve.mutate({ id, resolution })
      }
      busy={scenarios.resolve.isPending}
      unreachable={unreachable}
    />
  )
}

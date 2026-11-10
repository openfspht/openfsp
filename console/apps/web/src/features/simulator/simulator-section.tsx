import { SimulatorPanel } from "@workspace/design-system"

import { useSimulator } from "./use-simulator"

export function SimulatorSection() {
  const simulator = useSimulator()
  return (
    <SimulatorPanel
      operator={simulator.operator}
      onOperatorChange={simulator.setOperator}
      scenario={simulator.scenario.data?.scenario ?? "SUCCESS"}
      onScenarioChange={simulator.setScenario.mutate}
      waiting={
        simulator.payments.data?.data.filter((payment) => !payment.outcome) ??
        []
      }
      onResolve={(id, resolution) =>
        simulator.resolve.mutate({ id, resolution })
      }
      busy={simulator.resolve.isPending}
      unreachable={simulator.payments.isError}
    />
  )
}

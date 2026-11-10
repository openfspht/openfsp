import {
  AppHeader,
  ConsoleLayout,
  SimulatedPaymentsTable,
} from "@workspace/design-system"
import type { SimulatedOperator } from "@workspace/types"
import { useState } from "react"

import { JournalSection } from "@/features/journal/journal-section"
import { useSimulatedPayments } from "@/features/payments/use-simulated-payments"
import { ScenariosSection } from "@/features/scenarios/scenarios-section"
import { SimulationControlsSection } from "@/features/statements/simulation-controls-section"

export function App() {
  const [operator, setOperator] = useState<SimulatedOperator>("mock_openfsp")
  const { payments, deliverCallback } = useSimulatedPayments()
  const list = payments.data?.data ?? []
  return (
    <ConsoleLayout
      header={<AppHeader title="simulator" environment="test" />}
      main={
        <>
          <SimulatedPaymentsTable
            payments={list}
            onDeliverCallback={deliverCallback.mutate}
            busy={deliverCallback.isPending}
          />
          <JournalSection operator={operator} />
        </>
      }
      aside={
        <>
          <ScenariosSection
            operator={operator}
            onOperatorChange={setOperator}
            payments={list}
            unreachable={payments.isError}
          />
          <SimulationControlsSection operator={operator} />
        </>
      }
    />
  )
}

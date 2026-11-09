import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppHeader, ConsoleLayout, ErrorText, PaymentDetail, PaymentsTable, SimulatorPanel, type Resolution } from "@workspace/design-system"
import type { Scenario } from "@workspace/types"
import { useState } from "react"

import { gateway, SIMULATED_OPERATOR, simulator } from "@/lib/api"

const EVERY_SECOND = 1000

export function App() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries()
  const [selectedId, setSelectedId] = useState<string>()

  const payments = useQuery({ queryKey: ["gateway", "payments"], queryFn: gateway.payments, refetchInterval: EVERY_SECOND })
  const list = payments.data?.data ?? []
  const activeId = selectedId ?? list[0]?.id
  const detail = useQuery({
    queryKey: ["gateway", "payment", activeId],
    queryFn: () => gateway.payment(activeId!),
    enabled: activeId !== undefined,
    refetchInterval: EVERY_SECOND,
  })

  const scenario = useQuery({ queryKey: ["simulator", "scenario"], queryFn: simulator.defaultScenario })
  const simulated = useQuery({ queryKey: ["simulator", "payments"], queryFn: simulator.payments, refetchInterval: EVERY_SECOND })
  const setScenario = useMutation({ mutationFn: (next: Scenario) => simulator.setDefaultScenario(next), onSuccess: refresh })
  const resolve = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: Resolution }) => simulator.resolve(id, resolution.outcome, resolution.reason),
    onSuccess: refresh,
  })

  return (
    <ConsoleLayout
      header={<AppHeader title="console" environment="test" />}
      main={
        <>
          {payments.isError && <ErrorText>The gateway is not reachable.</ErrorText>}
          <PaymentsTable payments={list} selectedId={activeId} onSelect={setSelectedId} />
        </>
      }
      aside={
        <>
          <SimulatorPanel
            operator={SIMULATED_OPERATOR}
            scenario={scenario.data?.scenario ?? "SUCCESS"}
            onScenarioChange={setScenario.mutate}
            waiting={simulated.data?.data.filter((payment) => !payment.outcome) ?? []}
            onResolve={(id, resolution) => resolve.mutate({ id, resolution })}
            busy={resolve.isPending}
            unreachable={simulated.isError}
          />
          {detail.data && <PaymentDetail detail={detail.data} />}
        </>
      }
    />
  )
}

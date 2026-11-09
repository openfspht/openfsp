import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppHeader, ConsoleLayout, ErrorText, GatewayStatementCard, KeysCard, PaymentDetail, PaymentsTable, SimulatorPanel, type Resolution } from "@workspace/design-system"
import { SIMULATED_OPERATORS, type AttestedStatus, type Scenario, type ScopeName, type SimulatedOperator } from "@workspace/types"
import { useState } from "react"

import { gateway, simulator } from "@/lib/api"

const EVERY_SECOND = 1000
const ATTESTED_BY = "console operator"

export function App() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries()
  const [selectedId, setSelectedId] = useState<string>()
  const [operator, setOperator] = useState<SimulatedOperator>("mock_alpha")
  const [statementProvider, setStatementProvider] = useState<SimulatedOperator>("mock_alpha")

  const payments = useQuery({ queryKey: ["gateway", "payments"], queryFn: gateway.payments, refetchInterval: EVERY_SECOND })
  const list = payments.data?.data ?? []
  const activeId = selectedId ?? list[0]?.id
  const detail = useQuery({
    queryKey: ["gateway", "payment", activeId],
    queryFn: () => gateway.payment(activeId!),
    enabled: activeId !== undefined,
    refetchInterval: EVERY_SECOND,
  })

  const statement = useQuery({
    queryKey: ["gateway", "statement", statementProvider],
    queryFn: () => gateway.gatewayStatement(statementProvider),
    refetchInterval: EVERY_SECOND,
  })
  const keys = useQuery({ queryKey: ["gateway", "keys"], queryFn: gateway.keys })
  const issueKey = useMutation({
    mutationFn: ({ principal, scopes }: { principal: string; scopes: ScopeName[] }) => gateway.issueKey(principal, scopes),
    onSuccess: refresh,
  })
  const revokeKey = useMutation({ mutationFn: gateway.revokeKey, onSuccess: refresh })
  const scenario = useQuery({ queryKey: ["simulator", "scenario", operator], queryFn: () => simulator.defaultScenario(operator) })
  const simulated = useQuery({ queryKey: ["simulator", "payments"], queryFn: simulator.payments, refetchInterval: EVERY_SECOND })
  const setScenario = useMutation({ mutationFn: (next: Scenario) => simulator.setDefaultScenario(operator, next), onSuccess: refresh })
  const attest = useMutation({
    mutationFn: (status: AttestedStatus) => gateway.attest(activeId!, { person: ATTESTED_BY, status, evidence: "attested from the console" }),
    onSuccess: refresh,
  })
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
          <GatewayStatementCard
            statement={statement.data?.data[0]}
            provider={statementProvider}
            providers={SIMULATED_OPERATORS}
            onProviderChange={setStatementProvider}
          />
          <KeysCard
            keys={keys.data?.data ?? []}
            issuedSecret={issueKey.data?.secret}
            onIssue={(principal, scopes) => issueKey.mutate({ principal, scopes })}
            onRevoke={revokeKey.mutate}
            busy={issueKey.isPending || revokeKey.isPending}
          />
        </>
      }
      aside={
        <>
          <SimulatorPanel
            operator={operator}
            onOperatorChange={setOperator}
            scenario={scenario.data?.scenario ?? "SUCCESS"}
            onScenarioChange={setScenario.mutate}
            waiting={simulated.data?.data.filter((payment) => !payment.outcome) ?? []}
            onResolve={(id, resolution) => resolve.mutate({ id, resolution })}
            busy={resolve.isPending}
            unreachable={simulated.isError}
          />
          {detail.data && <PaymentDetail detail={detail.data} onAttest={attest.mutate} attesting={attest.isPending} />}
        </>
      }
    />
  )
}

import type { Scenario, SimulatedPayment } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Heading, Mono, Muted, Time } from "../atoms"
import { ResolutionActions, ScenarioSelect, type Resolution } from "../molecules"

interface SimulatorPanelProps {
  operator: string
  scenario: Scenario
  onScenarioChange: (scenario: Scenario) => void
  waiting: SimulatedPayment[]
  onResolve: (paymentId: string, resolution: Resolution) => void
  busy?: boolean
  unreachable?: boolean
}

export function SimulatorPanel({ operator, scenario, onScenarioChange, waiting, onResolve, busy, unreachable }: SimulatorPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Simulator · {operator}</CardTitle>
        <CardDescription>Choose how the next payments behave, or settle a waiting one by hand.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="flex items-center justify-between gap-4">
          <Muted className="text-sm">Next payments</Muted>
          <ScenarioSelect value={scenario} onChange={onScenarioChange} />
        </div>
        <div className="space-y-3">
          <Heading>Waiting at the operator</Heading>
          {unreachable && <ErrorText>The simulator is not reachable.</ErrorText>}
          {!unreachable && waiting.length === 0 && <EmptyText>Nothing is waiting.</EmptyText>}
          {waiting.map((payment) => (
            <div key={payment.payment_id} className="space-y-2 border-b pb-3 last:border-b-0">
              <div className="flex items-center justify-between text-sm">
                <Mono className="text-sm">{payment.payment_id}</Mono>
                <Muted className="tabular-nums">
                  {payment.amount} HTG · <Time value={payment.created_at} />
                </Muted>
              </div>
              <ResolutionActions disabled={busy} onResolve={(resolution) => onResolve(payment.payment_id, resolution)} />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

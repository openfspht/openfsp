import { SCENARIOS, SIMULATED_OPERATORS, type Scenario, type SimulatedOperator, type SimulatedPayment } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Heading, Mono, Muted, Time } from "../atoms"
import { OptionSelect, ResolutionActions, type Resolution } from "../molecules"

interface SimulatorPanelProps {
  operator: SimulatedOperator
  onOperatorChange: (operator: SimulatedOperator) => void
  scenario: Scenario
  onScenarioChange: (scenario: Scenario) => void
  waiting: SimulatedPayment[]
  onResolve: (paymentId: string, resolution: Resolution) => void
  busy?: boolean
  unreachable?: boolean
}

export function SimulatorPanel({ operator, onOperatorChange, scenario, onScenarioChange, waiting, onResolve, busy, unreachable }: SimulatorPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Simulator</CardTitle>
        <CardDescription>Choose how the next payments behave, or settle a waiting one by hand.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="flex items-center justify-between gap-4">
          <Muted className="text-sm">Operator</Muted>
          <OptionSelect value={operator} options={SIMULATED_OPERATORS} onChange={onOperatorChange} />
        </div>
        <div className="flex items-center justify-between gap-4">
          <Muted className="text-sm">Next payments</Muted>
          <OptionSelect value={scenario} options={SCENARIOS} onChange={onScenarioChange} />
        </div>
        <div className="space-y-3">
          <Heading>Waiting at the operator</Heading>
          {unreachable && <ErrorText>The simulator is not reachable.</ErrorText>}
          {!unreachable && waiting.length === 0 && <EmptyText>Nothing is waiting.</EmptyText>}
          {waiting.map((payment) => (
            <div key={payment.payment_id} className="space-y-2 border-b pb-3 last:border-b-0">
              <div className="flex items-center justify-between text-sm">
                <span>
                  <Mono className="text-sm">{payment.payment_id}</Mono> <Muted className="text-xs">{payment.operator}</Muted>
                </span>
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

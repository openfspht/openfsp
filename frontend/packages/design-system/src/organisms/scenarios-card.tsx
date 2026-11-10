import { SCENARIOS, type Scenario } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../atoms"
import { useCopy } from "../copy"
import { OptionSelect } from "../molecules"

export interface OperatorScenario {
  operator: string
  name: string
  scenario?: Scenario
}

interface ScenariosCardProps {
  operators: OperatorScenario[]
  onChange: (operator: string, scenario: Scenario) => void
}

export function ScenariosCard({ operators, onChange }: ScenariosCardProps) {
  const copy = useCopy().simulator.scenarios
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {operators.map(({ operator, name, scenario }) => (
          <div key={operator} className="flex items-center justify-between gap-4">
            <span className="text-sm">{name}</span>
            {scenario && <OptionSelect label={`${copy.next}: ${name}`} value={scenario} options={SCENARIOS} onChange={(next) => onChange(operator, next)} />}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

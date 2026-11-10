import type { Scenario } from "@workspace/types"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, ErrorText, Muted } from "../atoms"
import { useCopy } from "../copy"
import { OptionSelect } from "../molecules"

export interface OperatorScenario {
  operator: string
  name: string
  scenario?: Scenario
  options: readonly Scenario[]
  automatic: boolean
}

interface ScenariosCardProps {
  operators: OperatorScenario[]
  onChange: (operator: string, scenario: Scenario) => void
  onAutomaticChange: (operator: string, automatic: boolean) => void
  refused?: boolean
}

export function ScenariosCard({ operators, onChange, onAutomaticChange, refused }: ScenariosCardProps) {
  const copy = useCopy().simulator.scenarios
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {refused && <ErrorText>{copy.refused}</ErrorText>}
        {operators.map(({ operator, name, scenario, options, automatic }) => (
          <div key={operator} className="space-y-2">
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm font-medium">{name}</span>
              <Button size="xs" variant={automatic ? "default" : "outline"} aria-pressed={automatic} aria-label={copy.automaticLabel(name)} title={copy.automaticLabel(name)} onClick={() => onAutomaticChange(operator, !automatic)}>
                {copy.automatic}
              </Button>
            </div>
            {automatic ? (
              <Muted className="block text-sm">{copy.rotating(options.length)}</Muted>
            ) : (
              scenario && options.length > 0 && <OptionSelect label={`${copy.next}: ${name}`} value={scenario} options={options} onChange={(next) => onChange(operator, next)} className="w-full" />
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

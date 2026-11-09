import { SCENARIOS, type Scenario } from "@workspace/types"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@workspace/ui/components/select"

interface ScenarioSelectProps {
  value: Scenario
  onChange: (scenario: Scenario) => void
}

export function ScenarioSelect({ value, onChange }: ScenarioSelectProps) {
  return (
    <Select value={value} onValueChange={(next) => next && onChange(next as Scenario)}>
      <SelectTrigger className="w-56">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {SCENARIOS.map((name) => (
          <SelectItem key={name} value={name}>
            {name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

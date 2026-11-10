import { SCENARIOS, type Scenario } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Card, CardContent } from "../atoms"
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
  const copy = useCopy().simulator
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.operator}</TableHead>
              <TableHead className="text-right">{copy.scenarios.next}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {operators.map(({ operator, name, scenario }) => (
              <TableRow key={operator}>
                <TableCell>{name}</TableCell>
                <TableCell className="flex justify-end">
                  {scenario && <OptionSelect label={`${copy.scenarios.next}: ${name}`} value={scenario} options={SCENARIOS} onChange={(next) => onChange(operator, next)} />}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

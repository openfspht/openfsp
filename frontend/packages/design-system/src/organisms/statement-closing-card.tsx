import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Muted } from "../atoms"
import { useCopy } from "../copy"
import { LabeledInput, OptionSelect } from "../molecules"

interface StatementClosingCardProps<T extends string> {
  operator: T
  operators: readonly T[]
  nameOf: (operator: T) => string
  onOperatorChange: (operator: T) => void
  today?: string
  closedDays: string[]
  onCloseDay: (day: string) => void
  busy?: boolean
}

export function StatementClosingCard<T extends string>({ operator, operators, nameOf, onOperatorChange, today, closedDays, onCloseDay, busy }: StatementClosingCardProps<T>) {
  const copy = useCopy().simulator
  const [picked, setPicked] = useState<string>()
  const day = picked ?? today ?? ""
  const closed = closedDays.includes(day)
  const future = Boolean(today && day > today)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.statements.title}</CardTitle>
        <CardDescription>{copy.statements.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm">{copy.operator}</span>
          <OptionSelect label={copy.operator} value={operator} options={operators} labelOf={nameOf} onChange={onOperatorChange} />
        </div>
        <LabeledInput id="statement-day" label={copy.statements.day} type="date" max={today} value={day} onChange={(event) => setPicked(event.target.value)} />
        <Button disabled={busy || !day || closed || future} onClick={() => onCloseDay(day)}>
          {closed ? copy.statements.closed : copy.statements.close}
        </Button>
        {future && <Muted className="block text-sm">{copy.statements.future}</Muted>}
        <Muted className="block text-sm">{closedDays.length ? copy.statements.closedDays(closedDays.join(", ")) : copy.statements.none}</Muted>
      </CardContent>
    </Card>
  )
}

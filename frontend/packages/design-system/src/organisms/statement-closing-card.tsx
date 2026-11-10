import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Muted } from "../atoms"
import { useCopy } from "../copy"
import { LabeledInput } from "../molecules"

interface StatementClosingCardProps {
  operator: string
  today?: string
  closedDays: string[]
  onCloseDay: (day: string) => void
  busy?: boolean
}

export function StatementClosingCard({ operator, today, closedDays, onCloseDay, busy }: StatementClosingCardProps) {
  const copy = useCopy().simulator.statements
  const [picked, setPicked] = useState<string>()
  const day = picked ?? today ?? ""
  const closed = closedDays.includes(day)
  const future = Boolean(today && day > today)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description(operator)}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-56">
            <LabeledInput id="statement-day" label={copy.day} type="date" max={today} value={day} onChange={(event) => setPicked(event.target.value)} />
          </div>
          <Button disabled={busy || !day || closed || future} onClick={() => onCloseDay(day)}>
            {closed ? copy.closed : copy.close}
          </Button>
        </div>
        {future && <Muted className="block text-sm">{copy.future}</Muted>}
        <Muted className="block text-sm">{closedDays.length ? copy.closedDays(closedDays.join(", ")) : copy.none}</Muted>
      </CardContent>
    </Card>
  )
}

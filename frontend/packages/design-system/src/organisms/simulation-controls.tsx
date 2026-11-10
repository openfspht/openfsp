import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Heading, Muted } from "../atoms"
import { LabeledInput } from "../molecules"

interface SimulationControlsProps {
  operator: string
  today?: string
  closedDays: string[]
  onCloseDay: (day: string) => void
  onReset: () => void
  busy?: boolean
}

export function SimulationControls({ operator, today, closedDays, onCloseDay, onReset, busy }: SimulationControlsProps) {
  const [picked, setPicked] = useState<string>()
  const day = picked ?? today ?? ""
  const closed = closedDays.includes(day)
  const future = Boolean(today && day > today)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Statements</CardTitle>
        <CardDescription>Close a business day so the gateway can reconcile it. Days follow Haiti time.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-3">
          <Heading>Close a day for {operator}</Heading>
          <LabeledInput id="statement-day" label="Business day" type="date" max={today} value={day} onChange={(event) => setPicked(event.target.value)} />
          <Button disabled={busy || !day || closed || future} onClick={() => onCloseDay(day)}>
            {closed ? "Already closed" : "Close the day"}
          </Button>
          {future && <Muted className="block text-xs">This day has not started yet.</Muted>}
          <Muted className="block text-xs">{closedDays.length ? `Closed: ${closedDays.join(", ")}` : "No day closed yet."}</Muted>
        </div>
        <Button variant="destructive" disabled={busy} onClick={onReset}>
          Reset the simulator
        </Button>
      </CardContent>
    </Card>
  )
}

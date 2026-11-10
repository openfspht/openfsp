import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Heading, Muted } from "../atoms"
import { LabeledInput } from "../molecules"

interface SimulationControlsProps {
  operator: string
  onCloseDay: (day: string) => void
  onAdvanceClock: (seconds: number) => void
  onReset: () => void
  closedDay?: string
  busy?: boolean
}

const today = () => new Date().toISOString().slice(0, 10)

export function SimulationControls({ operator, onCloseDay, onAdvanceClock, onReset, closedDay, busy }: SimulationControlsProps) {
  const [day, setDay] = useState(today)
  const [seconds, setSeconds] = useState("3600")
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Statements and time</CardTitle>
        <CardDescription>Close a business day so the gateway can reconcile it, or move the simulated clock.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-3">
          <Heading>Close a day for {operator}</Heading>
          <LabeledInput id="statement-day" label="Business day" type="date" value={day} onChange={(event) => setDay(event.target.value)} />
          <Button disabled={busy || !day} onClick={() => onCloseDay(day)}>
            Close the day
          </Button>
          {closedDay && <Muted className="block text-xs">{closedDay} is closed.</Muted>}
        </div>
        <div className="space-y-3">
          <Heading>Clock</Heading>
          <LabeledInput id="clock-seconds" label="Advance by, in seconds" type="number" min={1} value={seconds} onChange={(event) => setSeconds(event.target.value)} />
          <Button variant="outline" disabled={busy || Number(seconds) < 1} onClick={() => onAdvanceClock(Number(seconds))}>
            Advance the clock
          </Button>
        </div>
        <Button variant="destructive" disabled={busy} onClick={onReset}>
          Reset the simulator
        </Button>
      </CardContent>
    </Card>
  )
}

import type { JournalEntry } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Mono, Muted, Time } from "../atoms"

interface RequestJournalProps {
  operator: string
  entries: JournalEntry[]
}

export function RequestJournal({ operator, entries }: RequestJournalProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Requests received</CardTitle>
        <CardDescription>Every call the gateway made to {operator}, credentials redacted.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {entries.length === 0 && <EmptyText>No request yet.</EmptyText>}
        {entries.map((entry, index) => (
          <div key={`${entry.received_at}-${index}`} className="space-y-1 border-b pb-3 last:border-b-0">
            <div className="flex items-center justify-between gap-2 text-sm">
              <Mono className="text-sm">
                {entry.method} {entry.path}
              </Mono>
              <Muted>
                <Time value={entry.received_at} />
              </Muted>
            </div>
            {entry.body && <pre className="overflow-x-auto bg-muted p-2 font-mono text-xs whitespace-pre-wrap break-all">{entry.body}</pre>}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

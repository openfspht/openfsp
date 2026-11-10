import type { JournalEntry } from "@workspace/types"

import { Heading, Muted, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"

const readable = (body: string) => {
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
}

function Part({ title, headers, body }: { title: string; headers: Record<string, string>; body: string }) {
  const copy = useCopy().simulator.journal
  const entries = Object.entries(headers)
  return (
    <section className="space-y-2">
      <Heading>{title}</Heading>
      {entries.length > 0 && (
        <dl className="grid grid-cols-[minmax(0,12rem)_minmax(0,1fr)] gap-x-4 gap-y-1 text-xs">
          {entries.map(([name, value]) => (
            <div key={name} className="contents">
              <dt className="truncate text-muted-foreground">{name}</dt>
              <dd className="break-all">
                <Tabular>{value}</Tabular>
              </dd>
            </div>
          ))}
        </dl>
      )}
      {body ? <pre className="max-h-64 overflow-auto bg-muted p-3 text-xs whitespace-pre-wrap break-all">{readable(body)}</pre> : <Muted className="block text-xs">{copy.noBody}</Muted>}
    </section>
  )
}

export function JournalExchange({ entry }: { entry: JournalEntry }) {
  const copy = useCopy().simulator.journal
  return (
    <div className="max-h-[70svh] space-y-6 overflow-y-auto">
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{copy.status}</dt>
        <dd>
          <Tabular>{entry.status}</Tabular> <Muted className="text-xs">{copy.duration(entry.duration_ms)}</Muted>
        </dd>
        <dt className="text-muted-foreground">{copy.received}</dt>
        <dd>
          <Time value={entry.received_at} />
        </dd>
        <dt className="text-muted-foreground">{copy.from}</dt>
        <dd>
          <Tabular>{entry.remote_address}</Tabular>
        </dd>
      </dl>
      <Part title={copy.request} headers={entry.headers} body={entry.body} />
      <Part title={copy.response} headers={entry.response_headers} body={entry.response_body} />
    </div>
  )
}

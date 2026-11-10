import { cn } from "@workspace/ui/lib/utils"

import { Badge, EmptyText, Mono, Muted, Time } from "../atoms"

export type ActivityTone = "neutral" | "positive" | "pending" | "critical"

export interface ActivityRow {
  id: string
  at: string
  tag: string
  tone: ActivityTone
  title: React.ReactNode
  detail?: string
  reference?: string
}

interface ActivitySpineProps {
  rows: ActivityRow[]
  selectedReference?: string
  onSelect?: (row: ActivityRow) => void
  empty: string
}

const dots: Record<ActivityTone, string> = {
  neutral: "bg-muted-foreground",
  positive: "bg-emerald-500",
  pending: "bg-accent",
  critical: "bg-destructive",
}

const badges: Record<ActivityTone, "secondary" | "outline" | "destructive" | "default"> = {
  neutral: "secondary",
  positive: "default",
  pending: "outline",
  critical: "destructive",
}

export function ActivitySpine({ rows, selectedReference, onSelect, empty }: ActivitySpineProps) {
  if (rows.length === 0) return <EmptyText className="py-10 text-center">{empty}</EmptyText>
  return (
    <ol className="relative">
      <span aria-hidden className="absolute top-0 bottom-0 left-[calc(6.125rem-0.5px)] w-px bg-border md:left-[calc(6.25rem-0.5px)]" />
      {rows.map((row) => {
        const selectable = onSelect !== undefined && row.reference !== undefined
        const selected = selectedReference !== undefined && row.reference === selectedReference
        return (
          <li key={row.id}>
            <button
              type="button"
              disabled={!selectable}
              onClick={() => selectable && onSelect(row)}
              aria-current={selected || undefined}
              className={cn(
                "grid w-full grid-cols-[5rem_1.25rem_minmax(0,1fr)] items-center gap-x-2 gap-y-1 py-2.5 text-left text-sm outline-none md:grid-cols-[5rem_1.5rem_8.5rem_minmax(0,1fr)]",
                "border-b border-border/60 focus-visible:bg-muted",
                selectable && "cursor-pointer hover:bg-muted/60",
                selected && "bg-muted"
              )}
            >
              <Muted className="text-xs tabular-nums">
                <Time value={row.at} />
              </Muted>
              <span className="relative flex justify-center md:row-span-1">
                <span className={cn("size-2.5 rounded-full ring-4", selected ? "ring-accent/40" : "ring-background", dots[row.tone])} />
              </span>
              <span className="col-start-3 md:col-start-auto">
                <Badge variant={badges[row.tone]}>{row.tag}</Badge>
              </span>
              <span className="col-start-3 flex min-w-0 items-baseline justify-between gap-3 md:col-start-auto">
                <span className="line-clamp-2 min-w-0 md:line-clamp-none md:truncate">{row.title}</span>
                {row.detail && <Mono className="hidden shrink-0 text-muted-foreground normal-case md:inline">{row.detail}</Mono>}
              </span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}

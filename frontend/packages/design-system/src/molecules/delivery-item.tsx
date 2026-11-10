import type { Delivery } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { Badge, Mono, Muted, Time } from "../atoms"

const badge: Record<Delivery["status"], "secondary" | "outline" | "destructive"> = {
  succeeded: "secondary",
  pending: "outline",
  abandoned: "destructive",
  stopped: "destructive",
}

export function DeliveryItem({ delivery }: { delivery: Delivery }) {
  return (
    <div className="space-y-1 text-xs">
      <div className="flex items-center justify-between gap-2">
        <Mono className="truncate text-muted-foreground">{delivery.url}</Mono>
        <Badge variant={badge[delivery.status]}>{delivery.status}</Badge>
      </div>
      {delivery.attempts.map((attempt) => (
        <Muted key={attempt.attempt} className="flex gap-3 pl-2 tabular-nums">
          <span>#{attempt.attempt}</span>
          <Time value={attempt.started_at} />
          <span className={cn(attempt.status_code !== undefined && attempt.status_code < 300 ? "text-emerald-600" : "text-red-600")}>
            {attempt.status_code ?? attempt.error}
          </span>
          <span>{attempt.duration_ms} ms</span>
        </Muted>
      ))}
      {delivery.next_attempt_at && (
        <Muted className="block pl-2">
          next attempt <Time value={delivery.next_attempt_at} />
        </Muted>
      )}
    </div>
  )
}

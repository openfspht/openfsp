import { Badge, Muted, Tabular, Time } from "@workspace/design-system"
import type { Delivery } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { useI18n } from "@/i18n/i18n-provider"

const badge: Record<Delivery["status"], "secondary" | "outline" | "destructive"> = {
    succeeded: "secondary",
    pending: "outline",
    abandoned: "destructive",
    stopped: "destructive",
}

export function DeliveryItem({ delivery }: { delivery: Delivery }) {
    const copy = useI18n().t
    return (
        <div className="space-y-1.5 text-sm">
            <div className="flex items-center justify-between gap-2">
                <Tabular className="truncate text-muted-foreground">{delivery.url}</Tabular>
                <Badge variant={badge[delivery.status]}>{copy.delivery[delivery.status]}</Badge>
            </div>
            {delivery.attempts.map((attempt) => (
                <Muted key={attempt.attempt} className="flex gap-3 pl-2 tabular-nums">
                    <span>#{attempt.attempt}</span>
                    <Time value={attempt.started_at} />
                    <span
                        className={cn(
                            attempt.status_code !== undefined && attempt.status_code < 300
                                ? "text-emerald-600"
                                : "text-red-600"
                        )}
                    >
                        {attempt.status_code ?? attempt.error}
                    </span>
                    <span>{attempt.duration_ms} ms</span>
                </Muted>
            ))}
            {delivery.next_attempt_at && (
                <Muted className="block pl-2">
                    {copy.delivery.nextAttempt} <Time value={delivery.next_attempt_at} />
                </Muted>
            )}
        </div>
    )
}

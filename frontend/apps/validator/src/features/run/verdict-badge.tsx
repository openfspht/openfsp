import { Muted } from "@workspace/design-system"
import type { LevelVerdict, ScenarioVerdict } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { useI18n } from "@/i18n/i18n-provider"

import type { RowStatus } from "./run-state"

const tones: Record<ScenarioVerdict | LevelVerdict, string> = {
    PASS: "bg-emerald-500",
    FAIL: "bg-red-500",
    INCONCLUSIVE: "bg-accent",
    INCOMPLETE: "bg-accent",
    ASSISTED: "bg-sky-500",
    NOT_APPLICABLE: "bg-muted-foreground",
}

function Dot({ className }: { className: string }) {
    return <span className={cn("size-2 shrink-0 rounded-full", className)} />
}

export function VerdictBadge({
    verdict,
    status = "done",
}: {
    verdict?: ScenarioVerdict
    status?: RowStatus
}) {
    const { t } = useI18n()
    if (status !== "done" || !verdict) {
        return (
            <span className="inline-flex items-center gap-2 text-sm">
                <Dot
                    className={cn(
                        "bg-muted-foreground/40",
                        status === "running" && "animate-pulse bg-accent"
                    )}
                />
                <Muted>{status === "running" ? t.run.running : t.run.pending}</Muted>
            </span>
        )
    }
    return (
        <span className="inline-flex items-center gap-2 text-sm font-medium">
            <Dot className={tones[verdict]} />
            {t.verdicts[verdict]}
        </span>
    )
}

export function LevelBadge({ verdict }: { verdict: LevelVerdict }) {
    const { t } = useI18n()
    return (
        <span className="inline-flex items-center gap-2 font-medium">
            <Dot className={tones[verdict]} />
            {t.levels[verdict]}
        </span>
    )
}

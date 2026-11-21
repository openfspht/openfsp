import { Muted, Tabular } from "@workspace/design-system"
import type { LevelVerdict, ScenarioVerdict } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { useI18n } from "@/i18n/i18n-provider"

import { verdictTone } from "./verdict-badge"

export type VerdictCounts = Partial<Record<ScenarioVerdict, number>>

const SHORTFALLS: ScenarioVerdict[] = ["FAIL", "INCONCLUSIVE", "ASSISTED"]

export function CoreTally({ counts, core }: { counts: VerdictCounts; core?: LevelVerdict }) {
    const { t } = useI18n()
    const met = counts.PASS ?? 0
    const total = Object.entries(counts)
        .filter(([verdict]) => verdict !== "NOT_APPLICABLE")
        .reduce((sum, [, count]) => sum + (count ?? 0), 0)
    if (total === 0) {
        return null
    }
    const shortfalls = SHORTFALLS.filter((verdict) => (counts[verdict] ?? 0) > 0)
    return (
        <div className="space-y-1.5">
            <div
                className="h-1.5 overflow-hidden rounded-full bg-stripe"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={total}
                aria-valuenow={met}
            >
                <div
                    className={cn("h-full rounded-full", core ? verdictTone(core) : "bg-primary")}
                    style={{ width: `${(met / total) * 100}%` }}
                />
            </div>
            <p className="text-sm">
                <Tabular>{t.tally.met(met, total)}</Tabular>
            </p>
            {shortfalls.length > 0 && (
                <Muted className="block text-sm">
                    <Tabular>
                        {shortfalls
                            .map((verdict) => t.tally.shortfall(verdict, counts[verdict] ?? 0))
                            .join(", ")}
                    </Tabular>
                </Muted>
            )}
        </div>
    )
}

export function countVerdicts(verdicts: ScenarioVerdict[]): VerdictCounts {
    const counts: VerdictCounts = {}
    for (const verdict of verdicts) {
        counts[verdict] = (counts[verdict] ?? 0) + 1
    }
    return counts
}

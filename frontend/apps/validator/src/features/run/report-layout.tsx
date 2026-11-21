import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Heading,
    Muted,
    Tabular,
} from "@workspace/design-system"
import type {
    ConformanceClaim,
    ConformanceLimit,
    LevelVerdict,
    ProfileResult,
    ScenarioVerdict,
} from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"
import { X } from "lucide-react"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"
import { useSuiteText } from "@/i18n/suite-text"

import { CoreTally, countVerdicts } from "./core-tally"
import { ResultsTable } from "./results-table"
import { CORE_LEVEL, groupByProfile, type ResultRow } from "./run-state"
import { LimitsCard } from "./limits-card"
import { LevelBadge, VerdictBadge, verdictTone } from "./verdict-badge"

const VERDICT_ORDER: ScenarioVerdict[] = [
    "FAIL",
    "INCONCLUSIVE",
    "ASSISTED",
    "NOT_APPLICABLE",
    "PASS",
]

interface ReportLayoutProps {
    rows: ResultRow[]
    core?: LevelVerdict
    profiles?: ProfileResult[]
    claim?: ConformanceClaim
    limits: ConformanceLimit[]
    specUrl?: string
    progress?: React.ReactNode
    details?: React.ReactNode
}

export function ReportLayout({
    rows,
    core,
    profiles,
    claim,
    limits,
    specUrl,
    progress,
    details,
}: ReportLayoutProps) {
    const { t } = useI18n()
    const [verdict, setVerdict] = useState<ScenarioVerdict>()
    const [level, setLevel] = useState<string>()
    const visible = rows.filter(
        (row) =>
            (level === undefined || row.planned.level === level) &&
            (verdict === undefined || row.result?.verdict === verdict)
    )
    const filtered = verdict !== undefined || level !== undefined
    return (
        <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)] lg:items-start">
            <aside className="space-y-6">
                <OverviewCard
                    rows={rows}
                    core={core}
                    claim={claim}
                    progress={progress}
                    verdict={verdict}
                    onVerdict={(next) => setVerdict(next === verdict ? undefined : next)}
                />
                <ProfilesPanel
                    rows={rows}
                    core={core}
                    profiles={profiles}
                    level={level}
                    onLevel={(next) => setLevel(next === level ? undefined : next)}
                />
                {details}
            </aside>
            <div className="min-w-0 space-y-6">
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <h2 className="font-heading text-lg font-semibold">
                        {t.filters.title}{" "}
                        <Muted className="font-normal">
                            <Tabular>{t.filters.count(visible.length, rows.length)}</Tabular>
                        </Muted>
                    </h2>
                    {level !== undefined && (
                        <FilterChip onClear={() => setLevel(undefined)}>
                            <Tabular>{level}</Tabular>
                        </FilterChip>
                    )}
                    {verdict !== undefined && (
                        <FilterChip onClear={() => setVerdict(undefined)}>
                            {t.verdicts[verdict]}
                        </FilterChip>
                    )}
                    {filtered && (
                        <Button
                            variant="ghost"
                            size="sm"
                            className="ml-auto"
                            onClick={() => {
                                setLevel(undefined)
                                setVerdict(undefined)
                            }}
                        >
                            {t.filters.reset}
                        </Button>
                    )}
                </div>
                <ResultsTable
                    rows={visible}
                    specUrl={specUrl}
                    empty={filtered ? t.filters.none : t.results.empty}
                />
                <LimitsCard limits={limits} specUrl={specUrl} />
            </div>
        </div>
    )
}

function FilterChip({ children, onClear }: { children: React.ReactNode; onClear: () => void }) {
    const { t } = useI18n()
    return (
        <span className="inline-flex items-center gap-1 rounded-full border bg-secondary py-0.5 pr-1 pl-3 text-sm">
            {children}
            <button
                type="button"
                aria-label={t.filters.reset}
                onClick={onClear}
                className="rounded-full p-1 text-muted-foreground hover:bg-background hover:text-foreground"
            >
                <X className="size-3.5" />
            </button>
        </span>
    )
}

function OverviewCard({
    rows,
    core,
    claim,
    progress,
    verdict,
    onVerdict,
}: {
    rows: ResultRow[]
    core?: LevelVerdict
    claim?: ConformanceClaim
    progress?: React.ReactNode
    verdict?: ScenarioVerdict
    onVerdict: (verdict: ScenarioVerdict) => void
}) {
    const { t } = useI18n()
    const counts = new Map<ScenarioVerdict, number>()
    for (const row of rows) {
        if (row.result) {
            counts.set(row.result.verdict, (counts.get(row.result.verdict) ?? 0) + 1)
        }
    }
    return (
        <Card>
            <CardContent className="space-y-5">
                <section className="space-y-2">
                    <Heading className="text-muted-foreground">{t.summary.core}</Heading>
                    <div className="font-heading text-2xl font-bold">
                        {core ? (
                            <LevelBadge verdict={core} />
                        ) : (
                            <Muted className="text-base font-normal">{progress}</Muted>
                        )}
                    </div>
                    <CoreTally
                        core={core}
                        counts={countVerdicts(
                            rows.flatMap((row) =>
                                row.planned.level === CORE_LEVEL && row.result
                                    ? [row.result.verdict]
                                    : []
                            )
                        )}
                    />
                    {claim && (
                        <p className="text-sm text-muted-foreground">
                            {claim.level === CORE_LEVEL ? (
                                <Tabular>
                                    {[claim.target, claim.level, ...claim.profiles].join(" + ")},{" "}
                                    {t.summary.protocol(claim.protocol_version)}
                                </Tabular>
                            ) : (
                                t.summary.noClaim
                            )}
                        </p>
                    )}
                </section>
                {counts.size > 0 && (
                    <section className="space-y-1">
                        <Heading className="text-muted-foreground">{t.filters.verdicts}</Heading>
                        <ul className="-mx-2">
                            {VERDICT_ORDER.filter((name) => counts.has(name)).map((name) => (
                                <li key={name}>
                                    <FilterRow
                                        selected={verdict === name}
                                        onClick={() => onVerdict(name)}
                                    >
                                        <span className="inline-flex items-center gap-2">
                                            <span
                                                className={cn(
                                                    "size-2 rounded-full",
                                                    verdictTone(name)
                                                )}
                                            />
                                            {t.verdicts[name]}
                                        </span>
                                        <Tabular className="text-muted-foreground">
                                            {counts.get(name)}
                                        </Tabular>
                                    </FilterRow>
                                </li>
                            ))}
                        </ul>
                    </section>
                )}
            </CardContent>
        </Card>
    )
}

function ProfilesPanel({
    rows,
    core,
    profiles,
    level,
    onLevel,
}: {
    rows: ResultRow[]
    core?: LevelVerdict
    profiles?: ProfileResult[]
    level?: string
    onLevel: (level: string) => void
}) {
    const { t } = useI18n()
    const text = useSuiteText()
    const coreRows = rows.filter((row) => row.planned.level === CORE_LEVEL)
    const groups = [
        { name: CORE_LEVEL, verdict: core, reason: undefined, rows: coreRows },
        ...groupByProfile(rows, profiles),
    ].filter((group) => group.rows.length > 0 || group.verdict)
    if (groups.length === 0) {
        return null
    }
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{t.profiles.title}</CardTitle>
                <CardDescription>{t.profiles.description}</CardDescription>
            </CardHeader>
            <CardContent>
                <ul className="-mx-2 space-y-0.5">
                    {groups.map((group) => {
                        const passed = group.rows.filter(
                            (row) => row.result?.verdict === "PASS"
                        ).length
                        const counted = group.rows.filter(
                            (row) => row.result?.verdict !== "NOT_APPLICABLE"
                        ).length
                        return (
                            <li key={group.name}>
                                <FilterRow
                                    selected={level === group.name}
                                    onClick={() => onLevel(group.name)}
                                    disabled={group.rows.length === 0}
                                >
                                    <span className="min-w-0">
                                        <Tabular className="block font-medium break-all">
                                            {group.name}
                                        </Tabular>
                                        <Muted className="block text-xs">
                                            {group.reason
                                                ? text.reason(group.reason)
                                                : t.filters.passed(passed, counted)}
                                        </Muted>
                                    </span>
                                    <span className="shrink-0 text-sm">
                                        {group.verdict ? (
                                            <LevelBadge verdict={group.verdict} />
                                        ) : (
                                            <VerdictBadge status="pending" />
                                        )}
                                    </span>
                                </FilterRow>
                            </li>
                        )
                    })}
                </ul>
            </CardContent>
        </Card>
    )
}

function FilterRow({
    selected,
    onClick,
    disabled,
    children,
}: {
    selected: boolean
    onClick: () => void
    disabled?: boolean
    children: React.ReactNode
}) {
    return (
        <button
            type="button"
            aria-pressed={selected}
            disabled={disabled}
            onClick={onClick}
            className={cn(
                "flex w-full items-center justify-between gap-3 rounded-md px-2 py-1.5 text-left text-sm transition-colors",
                "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:pointer-events-none",
                selected && "bg-secondary ring-1 ring-border"
            )}
        >
            {children}
        </button>
    )
}

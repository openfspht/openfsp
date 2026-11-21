import {
    Card,
    CardContent,
    CardHeader,
    CardTitle,
    EmptyText,
    Heading,
    Muted,
    Tabular,
} from "@workspace/design-system"
import type {
    ConformanceClaim,
    ConformanceLimit,
    LevelVerdict,
    ProfileResult,
} from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"
import { useSuiteText } from "@/i18n/suite-text"

import { specLink } from "./run-state"
import { LevelBadge } from "./verdict-badge"

interface RunSummaryProps {
    core?: LevelVerdict
    profiles?: ProfileResult[]
    claim?: ConformanceClaim
    children?: React.ReactNode
}

export function RunSummary({ core, profiles, claim, children }: RunSummaryProps) {
    const { t } = useI18n()
    return (
        <Card>
            <CardContent className="grid gap-8 md:grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)]">
                <section className="space-y-2">
                    <Heading className="text-muted-foreground">{t.summary.core}</Heading>
                    <div className="font-heading text-3xl font-bold">
                        {core ? <LevelBadge verdict={core} /> : <Muted>{children}</Muted>}
                    </div>
                </section>
                <section className="space-y-2">
                    <Heading className="text-muted-foreground">{t.summary.profiles}</Heading>
                    {profiles && profiles.length > 0 ? (
                        <ul className="space-y-1">
                            {profiles.map((profile) => (
                                <li
                                    key={profile.name}
                                    className="flex items-center justify-between gap-4 text-sm"
                                >
                                    <Tabular>{profile.name}</Tabular>
                                    <LevelBadge verdict={profile.verdict} />
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <EmptyText>{core ? t.summary.noProfiles : "-"}</EmptyText>
                    )}
                </section>
                <section className="space-y-2">
                    <Heading className="text-muted-foreground">{t.summary.claim}</Heading>
                    {claim ? (
                        claim.level === "core" ? (
                            <p className="text-sm">
                                <Tabular>
                                    {[claim.target, claim.level, ...claim.profiles].join(" + ")},{" "}
                                    {t.summary.protocol(claim.protocol_version)}
                                </Tabular>
                            </p>
                        ) : (
                            <EmptyText>{t.summary.noClaim}</EmptyText>
                        )
                    ) : (
                        <EmptyText>-</EmptyText>
                    )}
                </section>
            </CardContent>
        </Card>
    )
}

export function LimitsCard({ limits, specUrl }: { limits: ConformanceLimit[]; specUrl?: string }) {
    const { t } = useI18n()
    const text = useSuiteText()
    if (limits.length === 0) {
        return null
    }
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{t.summary.limits}</CardTitle>
            </CardHeader>
            <CardContent>
                <ul className="space-y-3 text-sm">
                    {limits.map((limit) => {
                        const link = specLink(specUrl, limit.reference)
                        return (
                            <li
                                key={limit.reference}
                                className="grid gap-1 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-4"
                            >
                                {link ? (
                                    <a
                                        href={link}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                                    >
                                        <Tabular>{limit.reference}</Tabular>
                                    </a>
                                ) : (
                                    <Muted>
                                        <Tabular>{limit.reference}</Tabular>
                                    </Muted>
                                )}
                                <span>{text.limit(limit.reference, limit.text)}</span>
                            </li>
                        )
                    })}
                </ul>
            </CardContent>
        </Card>
    )
}

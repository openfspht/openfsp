import { Card, CardContent, CardHeader, CardTitle, Muted, Tabular } from "@workspace/design-system"
import type { ConformanceLimit } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"
import { useSuiteText } from "@/i18n/suite-text"

import { specLink } from "./run-state"

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

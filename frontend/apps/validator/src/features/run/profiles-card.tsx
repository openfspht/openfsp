import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Muted,
  Tabular,
} from "@workspace/design-system"
import type { ProfileResult } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

import { groupByProfile, type ResultRow } from "./run-state"
import { LevelBadge, VerdictBadge } from "./verdict-badge"

export function ProfilesCard({
  rows,
  profiles,
}: {
  rows: ResultRow[]
  profiles?: ProfileResult[]
}) {
  const { t } = useI18n()
  const groups = groupByProfile(rows, profiles)
  if (groups.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{t.profiles.title}</CardTitle>
        <CardDescription>{t.profiles.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {groups.map((group) => (
          <section key={group.name} className="space-y-2">
            <div className="flex items-center justify-between gap-4 border-b pb-2">
              <Tabular className="font-medium">{group.name}</Tabular>
              {group.verdict ? (
                <LevelBadge verdict={group.verdict} />
              ) : (
                <Muted className="text-sm">{t.run.pending}</Muted>
              )}
            </div>
            <ul className="space-y-2">
              {group.rows.map((row) => (
                <li
                  key={row.planned.index}
                  className="flex items-start justify-between gap-4 text-sm"
                >
                  <span className="min-w-0">
                    <span className="block">{row.planned.title}</span>
                    <Muted className="text-xs">
                      <Tabular>
                        {row.planned.requirement_id} · {row.planned.scenario}
                      </Tabular>
                    </Muted>
                  </span>
                  <span className="shrink-0">
                    <VerdictBadge
                      verdict={row.result?.verdict}
                      status={row.status}
                    />
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </CardContent>
    </Card>
  )
}

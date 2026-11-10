import {
  ActivitySpine,
  Card,
  CardContent,
  EmptyText,
  ErrorText,
  FilterChips,
  PageHeader,
  SplitView,
  Stat,
  useCopy,
} from "@workspace/design-system"
import { Link, useSearchParams } from "react-router"

import { PaymentStory } from "@/features/payments/payment-story"
import { useI18n } from "@/i18n/i18n-provider"

import { ACTIVITY_FILTERS, isActivityFilter, type ActivityFilter } from "./activity-filters"
import { describeActivity } from "./describe-activity"
import { useActivity } from "./use-activity"

export function ActivityPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const [params, setParams] = useSearchParams()
  const source = params.get("source")
  const filter: ActivityFilter = isActivityFilter(source) ? source : "all"
  const selected = params.get("payment") ?? undefined
  const { items, unreachable, overview, providerNames } = useActivity(filter)
  const rows = items.map((item) => describeActivity(item, t, copy, providerNames))

  const update = (changes: Record<string, string | undefined>) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
      return next
    })

  const totals = overview?.payments ?? []
  const count = (status: string) => totals.find((total) => total.status === status)?.count ?? 0

  return (
    <>
      <PageHeader
        title={t.activity.title}
        description={overview ? t.activity.description(overview.business_date) : undefined}
        actions={
          <>
            <Stat value={totals.reduce((sum, total) => sum + total.count, 0)} label={t.activity.paymentsToday} />
            <Stat value={count("pending")} label={t.activity.pending} tone={count("pending") > 0 ? "pending" : "default"} />
            <Link to="/activity?source=deliveries" className="hover:opacity-80 focus-visible:outline-2 focus-visible:outline-ring">
              <Stat value={overview?.retrying_deliveries ?? 0} label={t.activity.retrying} tone={(overview?.retrying_deliveries ?? 0) > 0 ? "critical" : "default"} />
            </Link>
            <Link to="/webhooks" className="hover:opacity-80 focus-visible:outline-2 focus-visible:outline-ring">
              <Stat value={overview?.disabled_endpoints ?? 0} label={t.activity.disabled} tone={(overview?.disabled_endpoints ?? 0) > 0 ? "critical" : "default"} />
            </Link>
          </>
        }
      />
      <div className="mb-6">
        <FilterChips
          label={t.activity.filter}
          value={filter}
          onChange={(next) => update({ source: next === "all" ? undefined : next })}
          chips={ACTIVITY_FILTERS.map((value) => ({ value, label: t.activity.chips[value] }))}
        />
      </div>
      <SplitView
        main={
          <Card size="sm">
            <CardContent className="space-y-4">
              {unreachable && <ErrorText>{t.activity.unreachable}</ErrorText>}
              <ActivitySpine
                rows={rows}
                selectedReference={selected}
                onSelect={(row) => update({ payment: row.reference })}
                empty={t.activity.empty}
              />
            </CardContent>
          </Card>
        }
        aside={selected ? <PaymentStory id={selected} linked /> : <Card><CardContent><EmptyText>{t.activity.selectHint}</EmptyText></CardContent></Card>}
      />
    </>
  )
}

import {
    Card,
    CardContent,
    EmptyText,
    ErrorText,
    FilterChips,
    formatDate,
    formatDateTime,
    PageHeader,
    Pagination,
    SplitView,
    Stat,
    useCopy,
    useListParams,
} from "@workspace/design-system"
import { useMemo } from "react"
import { Link } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { PaymentStory } from "@/features/payments/payment-story"
import { TableExport } from "@/features/shell/table-export"
import { useProviders } from "@/features/providers/use-providers"
import { useI18n } from "@/i18n/i18n-provider"

import {
    ACTIVITY_FILTERS,
    isActivityFilter,
    kindsOf,
    sourcesOf,
    type ActivityFilter,
} from "./activity-filters"
import { ActivitySpine } from "./activity-spine"
import { describeActivity } from "./describe-activity"
import { useActivity } from "./use-activity"

const FILTERS = ["source", "payment"] as const

export function ActivityPage() {
    const { t } = useI18n()
    const copy = useCopy()
    const { page, values, set, setPage } = useListParams(FILTERS)
    const filter: ActivityFilter = isActivityFilter(values.source) ? values.source : "all"
    const selected = values.payment || undefined
    const { items, info, unreachable, overview } = useActivity(filter, page)
    const { names } = useProviders()
    const rows = useMemo(
        () => items.map((item) => describeActivity(item, t, copy, names)),
        [items, t, copy, names]
    )

    const totals = overview?.payments ?? []
    const table = {
        title: t.activity.title,
        filename: "activity",
        columns: [
            t.activity.exportColumns.at,
            t.activity.exportColumns.source,
            t.activity.exportColumns.event,
            t.activity.exportColumns.detail,
        ],
        rows: async () =>
            (
                await gatewayAdmin.activityExport({
                    kind: kindsOf(filter),
                    source: sourcesOf(filter),
                })
            ).data
                .map((item) => describeActivity(item, t, copy, names))
                .map((row) => [
                    formatDateTime(row.at, copy.locale),
                    row.tag,
                    String(row.title),
                    row.detail ?? "",
                ]),
        empty: rows.length === 0,
    }
    const count = (status: string) => totals.find((total) => total.status === status)?.count ?? 0

    return (
        <>
            <PageHeader
                title={t.activity.title}
                description={
                    overview
                        ? t.activity.description(formatDate(overview.business_date, copy.locale))
                        : undefined
                }
                actions={
                    <>
                        <Stat
                            value={totals.reduce((sum, total) => sum + total.count, 0)}
                            label={t.activity.paymentsToday}
                        />
                        <Stat
                            value={count("pending")}
                            label={t.activity.pending}
                            tone={count("pending") > 0 ? "pending" : "default"}
                        />
                        <Link
                            to="/activity?source=deliveries"
                            className="hover:opacity-80 focus-visible:outline-2 focus-visible:outline-ring"
                        >
                            <Stat
                                value={overview?.retrying_deliveries ?? 0}
                                label={t.activity.retrying}
                                tone={
                                    (overview?.retrying_deliveries ?? 0) > 0
                                        ? "critical"
                                        : "default"
                                }
                            />
                        </Link>
                        <Link
                            to="/webhooks"
                            className="hover:opacity-80 focus-visible:outline-2 focus-visible:outline-ring"
                        >
                            <Stat
                                value={overview?.disabled_endpoints ?? 0}
                                label={t.activity.disabled}
                                tone={
                                    (overview?.disabled_endpoints ?? 0) > 0 ? "critical" : "default"
                                }
                            />
                        </Link>
                    </>
                }
            />
            <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
                <FilterChips
                    label={t.activity.filter}
                    value={filter}
                    onChange={(next) => set({ source: next === "all" ? "" : next })}
                    chips={ACTIVITY_FILTERS.map((value) => ({
                        value,
                        label: t.activity.chips[value],
                    }))}
                />
                <TableExport table={table} />
            </div>
            <SplitView
                main={
                    <Card size="sm">
                        <CardContent className="space-y-4">
                            {unreachable && <ErrorText>{t.activity.unreachable}</ErrorText>}
                            <ActivitySpine
                                rows={rows}
                                selectedReference={selected}
                                onSelect={(row) =>
                                    set({
                                        payment: row.reference,
                                        page: page > 0 ? String(page) : "",
                                    })
                                }
                                empty={t.activity.empty}
                            />
                            {info && (
                                <Pagination
                                    page={info.number}
                                    totalPages={info.total_pages}
                                    totalElements={info.total_elements}
                                    onChange={setPage}
                                />
                            )}
                        </CardContent>
                    </Card>
                }
                aside={
                    selected ? (
                        <PaymentStory id={selected} linked />
                    ) : (
                        <Card>
                            <CardContent>
                                <EmptyText>{t.activity.selectHint}</EmptyText>
                            </CardContent>
                        </Card>
                    )
                }
            />
        </>
    )
}

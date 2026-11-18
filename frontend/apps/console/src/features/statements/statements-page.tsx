import { useQuery } from "@tanstack/react-query"
import {
    ActionError,
    Button,
    ErrorText,
    FilterChips,
    Input,
    Label,
    OptionSelect,
    PageHeader,
    TableToolbar,
    useCopy,
    useListParams,
} from "@workspace/design-system"
import { Download } from "lucide-react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useProviders } from "@/features/providers/use-providers"
import { AccountFilter } from "@/features/accounts/account-select"
import { useI18n } from "@/i18n/i18n-provider"

import {
    dayPeriod,
    isPeriodMode,
    localToday,
    MAX_RANGE_DAYS,
    PERIOD_MODES,
    type PeriodMode,
    type PeriodProblem,
    rangePeriod,
} from "./statement-period"
import { StatementTable } from "./statement-table"
import { useStatement } from "./use-statement"

const FILTERS = ["provider", "period", "day", "from", "to", "account"] as const
const PERIOD_ERROR = "statement-period-error"

export function StatementsPage() {
    const { t } = useI18n()
    const copy = t.statements
    const filterLabels = useCopy().filters
    const { ids, names } = useProviders()
    const { values, set } = useListParams(FILTERS)
    const businessDate = useQuery({
        queryKey: queryKeys.overview,
        queryFn: gatewayAdmin.overview,
        staleTime: Infinity,
        select: (overview) => overview.business_date,
    })
    const today = businessDate.data ?? localToday()
    const provider = values.provider || ids[0]
    const mode: PeriodMode = isPeriodMode(values.period) ? values.period : "day"
    const day = values.day || today
    const from = values.from || today
    const to = values.to || today
    const period = mode === "day" ? dayPeriod(day) : rangePeriod(from, to)
    const filters =
        provider && period.params
            ? { provider, ...period.params, account: values.account }
            : undefined
    const statement = useStatement(filters)
    const problems: Record<PeriodProblem, string> = {
        incomplete: copy.incomplete,
        reversed: copy.reversed,
        "too-long": copy.tooLong(MAX_RANGE_DAYS),
    }
    const invalid = period.problem !== undefined
    const dateProps = {
        type: "date",
        className: "w-44",
        "aria-invalid": invalid || undefined,
        "aria-describedby": invalid ? PERIOD_ERROR : undefined,
    } as const
    const exportCsv = () => {
        if (filters) {
            const span = mode === "day" ? day : `${from}-${to}`
            statement.exportCsv.mutate({
                filters,
                filename: `statement-${filters.provider}-${span}.csv`,
            })
        }
    }
    return (
        <>
            <PageHeader
                title={copy.title}
                description={copy.description}
                actions={
                    <Button
                        size="sm"
                        variant="outline"
                        disabled={statement.entries.length === 0 || statement.exportCsv.isPending}
                        onClick={exportCsv}
                    >
                        <Download />
                        {copy.exportCsv}
                    </Button>
                }
            />
            <ActionError error={statement.exportCsv.error} className="mb-4" />
            {statement.error && <ErrorText className="mb-4">{t.activity.unreachable}</ErrorText>}
            <TableToolbar>
                <AccountFilter value={values.account} onChange={(account) => set({ account })} />
                {provider && (
                    <OptionSelect
                        label={filterLabels.provider}
                        value={provider}
                        options={ids}
                        labelOf={(id) => names[id] ?? id}
                        onChange={(value) => set({ provider: value })}
                    />
                )}
                <FilterChips
                    label={copy.period}
                    value={mode}
                    chips={PERIOD_MODES.map((value) => ({ value, label: copy.periods[value] }))}
                    onChange={(value) => set({ period: value })}
                />
                {mode === "day" ? (
                    <Input
                        {...dateProps}
                        aria-label={copy.day}
                        value={day}
                        onChange={(event) => set({ day: event.target.value })}
                    />
                ) : (
                    <>
                        <span className="flex items-center gap-2">
                            <Label htmlFor="statement-from">{copy.from}</Label>
                            <Input
                                {...dateProps}
                                id="statement-from"
                                max={to}
                                value={from}
                                onChange={(event) => set({ from: event.target.value })}
                            />
                        </span>
                        <span className="flex items-center gap-2">
                            <Label htmlFor="statement-to">{copy.to}</Label>
                            <Input
                                {...dateProps}
                                id="statement-to"
                                min={from}
                                value={to}
                                onChange={(event) => set({ to: event.target.value })}
                            />
                        </span>
                    </>
                )}
            </TableToolbar>
            {period.problem && (
                <ErrorText id={PERIOD_ERROR} role="alert" className="mb-4">
                    {problems[period.problem]}
                </ErrorText>
            )}
            <StatementTable
                entries={statement.entries}
                empty={
                    provider === undefined
                        ? copy.noProvider
                        : statement.loaded
                          ? copy.empty
                          : undefined
                }
            />
            {statement.hasMore && (
                <div className="mt-4 flex justify-center">
                    <Button
                        variant="outline"
                        disabled={statement.loadingMore}
                        onClick={statement.loadMore}
                    >
                        {copy.loadMore}
                    </Button>
                </div>
            )}
        </>
    )
}

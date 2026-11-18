import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
    ActionError,
    Button,
    ErrorText,
    Modal,
    Muted,
    OptionSelect,
    PAGE_SIZE,
    PageHeader,
    Pagination,
    SearchField,
    SplitView,
    TableToolbar,
    useCopy,
    useDebounced,
    useListParams,
} from "@workspace/design-system"
import type { SimulatedPayment } from "@workspace/types"
import { useRef, useState } from "react"

import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
import { ResolutionActions, type Resolution } from "@/features/activity/resolution-actions"
import { SimulatedPaymentsTable } from "@/features/activity/simulated-payments-table"
import { ScenariosSection } from "@/features/scenarios/scenarios-section"
import { ALL, useOperators } from "@/features/shell/use-operators"
import { useI18n } from "@/i18n/i18n-provider"

const FILTERS = ["q", "operator", "state"] as const
const STATUS_OF = {
    waiting: "pending",
    successful: "succeeded",
    failed: "failed",
    expired: "expired",
} as const
type State = keyof typeof STATUS_OF
const STATES = Object.keys(STATUS_OF) as State[]

export function ActivityPage() {
    const { t } = useI18n()
    const copy = useCopy()
    const queryClient = useQueryClient()
    const { operators, names, nameOf } = useOperators()
    const [selected, setSelected] = useState<SimulatedPayment>()
    const selectedTrigger = useRef<HTMLElement>(null)
    const { page, values, sort, set, toggleSort, setPage, sortParams } = useListParams(FILTERS)
    const q = useDebounced(values.q)
    const params = {
        q,
        operator: values.operator,
        state: values.state,
        ...sortParams,
        page,
        size: PAGE_SIZE,
    }
    const payments = useQuery({
        queryKey: queryKeys.payments(params),
        queryFn: () => simulatorControl.payments(params),
        refetchInterval: LIVE,
        placeholderData: (previous) => previous,
    })
    const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.allPayments })
    const resolve = useMutation({
        mutationFn: ({ id, resolution }: { id: string; resolution: Resolution }) =>
            simulatorControl.resolve(id, resolution.outcome, resolution.reason),
        onSuccess: () => {
            setSelected(undefined)
            return refresh()
        },
    })
    const deliverCallback = useMutation({
        mutationFn: simulatorControl.deliverCallback,
        onSuccess: () => {
            setSelected(undefined)
            return refresh()
        },
    })
    const info = payments.data?.page
    return (
        <>
            <PageHeader title={t.activity.title} description={t.activity.description} />
            {payments.isError && <ErrorText className="mb-4">{t.unreachable}</ErrorText>}
            <SplitView
                main={
                    <div>
                        <TableToolbar>
                            <SearchField
                                value={values.q}
                                onChange={(value) => set({ q: value })}
                                placeholder={t.activity.search}
                            />
                            <OptionSelect
                                label={t.operator}
                                value={values.operator || ALL}
                                options={[ALL, ...operators]}
                                labelOf={(value) =>
                                    value === ALL ? t.allOperators : nameOf(value)
                                }
                                onChange={(value) => set({ operator: value === ALL ? "" : value })}
                            />
                            <OptionSelect
                                label={copy.filters.status}
                                value={values.state || ALL}
                                options={[ALL, ...STATES]}
                                labelOf={(value) =>
                                    value === ALL
                                        ? `${copy.filters.status}: ${copy.filters.all}`
                                        : copy.status[STATUS_OF[value as State]]
                                }
                                onChange={(value) => set({ state: value === ALL ? "" : value })}
                            />
                        </TableToolbar>
                        <SimulatedPaymentsTable
                            payments={payments.data?.data ?? []}
                            operatorNames={names}
                            sort={sort}
                            onSort={toggleSort}
                            onSelect={(payment, trigger) => {
                                selectedTrigger.current = trigger
                                resolve.reset()
                                deliverCallback.reset()
                                setSelected(payment)
                            }}
                        />
                        {info && (
                            <Pagination
                                page={info.number}
                                totalPages={info.total_pages}
                                totalElements={info.total_elements}
                                onChange={setPage}
                            />
                        )}
                    </div>
                }
                aside={<ScenariosSection />}
            />
            <Modal
                open={selected !== undefined}
                onClose={() => setSelected(undefined)}
                finalFocus={selectedTrigger}
                title={selected?.payment_id ?? ""}
                description={
                    selected?.outcome
                        ? t.payments.callbackDescription
                        : t.payments.settleDescription
                }
            >
                {selected && !selected.outcome && (
                    <ResolutionActions
                        disabled={resolve.isPending}
                        onResolve={(resolution) =>
                            resolve.mutate({ id: selected.payment_id, resolution })
                        }
                    />
                )}
                {selected?.outcome &&
                    (selected.callback_url ? (
                        <Button
                            disabled={deliverCallback.isPending}
                            onClick={() => deliverCallback.mutate(selected.payment_id)}
                        >
                            {t.payments.sendCallback}
                        </Button>
                    ) : (
                        <Muted className="text-sm">{t.payments.noCallback}</Muted>
                    ))}
                <ActionError error={resolve.error ?? deliverCallback.error} className="mt-4" />
            </Modal>
        </>
    )
}

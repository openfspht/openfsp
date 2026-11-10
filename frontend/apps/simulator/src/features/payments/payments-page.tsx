import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  ErrorText,
  Modal,
  OptionSelect,
  PAGE_SIZE,
  PageHeader,
  Pagination,
  ResolutionActions,
  SearchField,
  SimulatedPaymentsTable,
  TableToolbar,
  useCopy,
  useDebounced,
  useListParams,
  type Resolution,
} from "@workspace/design-system"
import type { SimulatedPayment } from "@workspace/types"
import { useState } from "react"

import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
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

export function PaymentsPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const labels = copy.simulator
  const queryClient = useQueryClient()
  const { operators, names, nameOf } = useOperators()
  const [settling, setSettling] = useState<SimulatedPayment>()
  const { page, values, sort, set, toggleSort, setPage, sortParams } =
    useListParams(FILTERS)
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
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: queryKeys.allPayments })
  const resolve = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: Resolution }) =>
      simulatorControl.resolve(id, resolution.outcome, resolution.reason),
    onSuccess: () => {
      setSettling(undefined)
      return refresh()
    },
  })
  const deliverCallback = useMutation({
    mutationFn: simulatorControl.deliverCallback,
    onSuccess: refresh,
  })
  const info = payments.data?.page
  return (
    <>
      <PageHeader
        title={t.payments.title}
        description={t.payments.description}
      />
      {payments.isError && (
        <ErrorText className="mb-4">{t.unreachable}</ErrorText>
      )}
      <TableToolbar>
        <SearchField
          value={values.q}
          onChange={(value) => set({ q: value })}
          placeholder={t.payments.search}
        />
        <OptionSelect
          label={labels.operator}
          value={values.operator || ALL}
          options={[ALL, ...operators]}
          labelOf={(value) =>
            value === ALL ? labels.allOperators : nameOf(value)
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
        onSettle={setSettling}
        onDeliverCallback={deliverCallback.mutate}
        busy={deliverCallback.isPending}
      />
      {info && (
        <Pagination
          page={info.number}
          totalPages={info.total_pages}
          totalElements={info.total_elements}
          onChange={setPage}
        />
      )}
      <Modal
        open={settling !== undefined}
        onClose={() => setSettling(undefined)}
        title={labels.payments.settleTitle(settling?.payment_id ?? "")}
        description={labels.payments.settleDescription}
      >
        <ResolutionActions
          disabled={resolve.isPending}
          onResolve={(resolution) =>
            settling && resolve.mutate({ id: settling.payment_id, resolution })
          }
        />
      </Modal>
    </>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  ErrorText,
  OptionSelect,
  PageHeader,
  StatementClosingCard,
  TableToolbar,
  useCopy,
  useListParams,
} from "@workspace/design-system"

import { queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
import { useOperators } from "@/features/shell/use-operators"
import { useI18n } from "@/i18n/i18n-provider"

const FILTERS = ["operator"] as const

export function StatementsPage() {
  const { t } = useI18n()
  const copy = useCopy().simulator
  const queryClient = useQueryClient()
  const { operators, nameOf, pick } = useOperators()
  const { values, set } = useListParams(FILTERS)
  const operator = pick(values.operator)
  const days = useQuery({
    queryKey: queryKeys.statementDays(operator),
    queryFn: () => simulatorControl.statementDays(operator),
  })
  const closeDay = useMutation({
    mutationFn: (day: string) => simulatorControl.closeDay(operator, day),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.statementDays(operator),
      }),
  })
  return (
    <>
      <PageHeader
        title={t.statements.title}
        description={t.statements.description}
      />
      {days.isError && <ErrorText className="mb-4">{t.unreachable}</ErrorText>}
      <TableToolbar>
        <OptionSelect
          label={copy.operator}
          value={operator}
          options={operators}
          labelOf={nameOf}
          onChange={(value) => set({ operator: value })}
        />
      </TableToolbar>
      <StatementClosingCard
        key={operator}
        operator={nameOf(operator)}
        today={days.data?.today}
        closedDays={days.data?.closed ?? []}
        onCloseDay={closeDay.mutate}
        busy={closeDay.isPending}
      />
    </>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { SimulatedOperator } from "@workspace/types"
import { useState } from "react"

import { queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
import { useOperators } from "@/features/shell/use-operators"
import { StatementClosingCard } from "@/features/statements/statement-closing-card"

export function StatementsSection() {
  const queryClient = useQueryClient()
  const { operators, nameOf } = useOperators()
  const [operator, setOperator] = useState<SimulatedOperator>(operators[0])
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
    <StatementClosingCard
      key={operator}
      operator={operator}
      operators={operators}
      nameOf={nameOf}
      onOperatorChange={setOperator}
      today={days.data?.today}
      closedDays={days.data?.closed ?? []}
      onCloseDay={closeDay.mutate}
      busy={closeDay.isPending}
      error={closeDay.error}
    />
  )
}

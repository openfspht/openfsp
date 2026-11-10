import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { SimulationControls } from "@workspace/design-system"
import type { SimulatedOperator } from "@workspace/types"

import { queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"

export function SimulationControlsSection({
  operator,
}: {
  operator: SimulatedOperator
}) {
  const queryClient = useQueryClient()
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
  const reset = useMutation({
    mutationFn: simulatorControl.reset,
    onSuccess: () => queryClient.invalidateQueries(),
  })
  return (
    <SimulationControls
      operator={operator}
      today={days.data?.today}
      closedDays={days.data?.closed ?? []}
      onCloseDay={closeDay.mutate}
      onReset={() => reset.mutate()}
      busy={closeDay.isPending || reset.isPending}
    />
  )
}

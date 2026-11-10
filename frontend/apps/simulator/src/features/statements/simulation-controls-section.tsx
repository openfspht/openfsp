import { useMutation, useQueryClient } from "@tanstack/react-query"
import { SimulationControls } from "@workspace/design-system"
import type { SimulatedOperator } from "@workspace/types"

import { simulatorControl } from "@/api/simulator-control"

export function SimulationControlsSection({
  operator,
}: {
  operator: SimulatedOperator
}) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries()
  const closeDay = useMutation({
    mutationFn: (day: string) => simulatorControl.closeDay(operator, day),
  })
  const advance = useMutation({
    mutationFn: simulatorControl.advanceClock,
    onSuccess: refresh,
  })
  const reset = useMutation({
    mutationFn: simulatorControl.reset,
    onSuccess: refresh,
  })
  return (
    <SimulationControls
      operator={operator}
      onCloseDay={closeDay.mutate}
      onAdvanceClock={advance.mutate}
      onReset={() => reset.mutate()}
      closedDay={closeDay.isSuccess ? closeDay.variables : undefined}
      busy={closeDay.isPending || advance.isPending || reset.isPending}
    />
  )
}

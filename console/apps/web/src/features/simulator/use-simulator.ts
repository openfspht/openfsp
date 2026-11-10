import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { Resolution } from "@workspace/design-system"
import type { Scenario, SimulatedOperator } from "@workspace/types"
import { useState } from "react"
import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"

export function useSimulator() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries()
  const [operator, setOperator] = useState<SimulatedOperator>("mock_alpha")
  const scenario = useQuery({
    queryKey: queryKeys.defaultScenario(operator),
    queryFn: () => simulatorControl.defaultScenario(operator),
  })
  const payments = useQuery({
    queryKey: queryKeys.simulatedPayments,
    queryFn: simulatorControl.payments,
    refetchInterval: LIVE,
  })
  const setScenario = useMutation({
    mutationFn: (next: Scenario) =>
      simulatorControl.setDefaultScenario(operator, next),
    onSuccess: refresh,
  })
  const resolve = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: Resolution }) =>
      simulatorControl.resolve(id, resolution.outcome, resolution.reason),
    onSuccess: refresh,
  })
  return { operator, setOperator, scenario, payments, setScenario, resolve }
}

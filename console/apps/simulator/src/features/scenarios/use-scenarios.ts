import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { Resolution } from "@workspace/design-system"
import type { Scenario, SimulatedOperator } from "@workspace/types"

import { queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"

export function useScenarios(operator: SimulatedOperator) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries()
  const scenario = useQuery({
    queryKey: queryKeys.defaultScenario(operator),
    queryFn: () => simulatorControl.defaultScenario(operator),
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
  return { scenario, setScenario, resolve }
}

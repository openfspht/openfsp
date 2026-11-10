import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"

export function useSimulatedPayments() {
  const queryClient = useQueryClient()
  const payments = useQuery({
    queryKey: queryKeys.payments,
    queryFn: simulatorControl.payments,
    refetchInterval: LIVE,
  })
  const deliverCallback = useMutation({
    mutationFn: simulatorControl.deliverCallback,
    onSuccess: () => queryClient.invalidateQueries(),
  })
  return { payments, deliverCallback }
}

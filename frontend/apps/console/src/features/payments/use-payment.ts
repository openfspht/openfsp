import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AttestedStatus } from "@workspace/types"

import { gatewayAdmin } from "@/api/gateway-admin"
import {
  LIVE,
  PENDING_REFRESH,
  queryKeys,
  type QueryParams,
} from "@/api/query-keys"

const ATTESTED_BY = "console operator"
const EVIDENCE = "attested from the console"

export function usePayment(id: string) {
  const queryClient = useQueryClient()
  const detail = useQuery({
    queryKey: queryKeys.payment(id),
    queryFn: () => gatewayAdmin.payment(id),
    refetchInterval: (query) =>
      query.state.data?.payment.status === "pending" ? PENDING_REFRESH : LIVE,
  })
  const attest = useMutation({
    mutationFn: (status: AttestedStatus) =>
      gatewayAdmin.attest(id, {
        person: ATTESTED_BY,
        status,
        evidence: EVIDENCE,
      }),
    onSuccess: () =>
      Promise.all(
        [
          queryKeys.payment(id),
          queryKeys.allPayments,
          queryKeys.allActivity,
          queryKeys.overview,
        ].map((queryKey) => queryClient.invalidateQueries({ queryKey }))
      ),
  })
  return { detail: detail.data, missing: detail.isError, attest }
}

export function usePayments(params: QueryParams) {
  const payments = useQuery({
    queryKey: queryKeys.payments(params),
    queryFn: () => gatewayAdmin.payments(params),
    refetchInterval: LIVE,
    placeholderData: (previous) => previous,
  })
  return {
    list: payments.data?.data ?? [],
    page: payments.data?.page,
    unreachable: payments.isError,
  }
}

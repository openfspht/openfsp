import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AttestedStatus } from "@workspace/types"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

const ATTESTED_BY = "console operator"
const EVIDENCE = "attested from the console"

export function usePayment(id: string) {
  const queryClient = useQueryClient()
  const detail = useQuery({ queryKey: queryKeys.payment(id), queryFn: () => gatewayAdmin.payment(id), refetchInterval: LIVE })
  const attest = useMutation({
    mutationFn: (status: AttestedStatus) => gatewayAdmin.attest(id, { person: ATTESTED_BY, status, evidence: EVIDENCE }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["gateway"] }),
  })
  return { detail: detail.data, missing: detail.isError, attest }
}

export function usePayments() {
  const payments = useQuery({ queryKey: queryKeys.payments, queryFn: gatewayAdmin.payments, refetchInterval: LIVE })
  return { list: payments.data?.data ?? [], unreachable: payments.isError }
}

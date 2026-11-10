import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AttestedStatus } from "@workspace/types"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

const ATTESTED_BY = "console operator"
const EVIDENCE = "attested from the console"

export function usePayments() {
  const queryClient = useQueryClient()
  const [selectedId, select] = useState<string>()
  const payments = useQuery({
    queryKey: queryKeys.payments,
    queryFn: gatewayAdmin.payments,
    refetchInterval: LIVE,
  })
  const list = payments.data?.data ?? []
  const activeId = selectedId ?? list[0]?.id
  const detail = useQuery({
    queryKey: queryKeys.payment(activeId),
    queryFn: () => gatewayAdmin.payment(activeId!),
    enabled: activeId !== undefined,
    refetchInterval: LIVE,
  })
  const attest = useMutation({
    mutationFn: (status: AttestedStatus) =>
      gatewayAdmin.attest(activeId!, {
        person: ATTESTED_BY,
        status,
        evidence: EVIDENCE,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["gateway"] }),
  })
  return {
    list,
    unreachable: payments.isError,
    activeId,
    select,
    detail: detail.data,
    attest,
  }
}

export type PaymentsState = ReturnType<typeof usePayments>

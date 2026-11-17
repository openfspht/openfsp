import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AttestationRequest } from "@workspace/types"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, PENDING_REFRESH, queryKeys, type QueryParams } from "@/api/query-keys"

export function usePayment(id: string) {
    const queryClient = useQueryClient()
    const detail = useQuery({
        queryKey: queryKeys.payment(id),
        queryFn: () => gatewayAdmin.payment(id),
        refetchInterval: (query) =>
            query.state.data?.payment.status === "pending" ? PENDING_REFRESH : LIVE,
    })
    const attest = useMutation({
        mutationFn: (attestation: AttestationRequest) => gatewayAdmin.attest(id, attestation),
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

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { TERMINAL_STATUSES, type AttestationRequest, type PaymentStatus } from "@workspace/types"
import type { QueryParams } from "@workspace/http"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, PENDING_REFRESH, queryKeys } from "@/api/query-keys"

const isOpen = (status: PaymentStatus | undefined) =>
    status !== undefined && !TERMINAL_STATUSES.has(status)

export function usePayment(id: string) {
    const queryClient = useQueryClient()
    const detail = useQuery({
        queryKey: queryKeys.payment(id),
        queryFn: () => gatewayAdmin.payment(id),
        refetchInterval: (query) =>
            isOpen(query.state.data?.payment.status) ? PENDING_REFRESH : LIVE,
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

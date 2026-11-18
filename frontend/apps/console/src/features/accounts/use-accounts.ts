import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

export function useAccounts() {
    return useQuery({
        queryKey: queryKeys.accounts,
        queryFn: gatewayAdmin.accounts,
        select: (collection) => collection.data,
    })
}

export function useOpenAccount() {
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: gatewayAdmin.openAccount,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.accounts }),
    })
}

export function useAccountNames() {
    const accounts = useAccounts()
    const names = new Map((accounts.data ?? []).map((account) => [account.id, account.name]))
    return (id: string) => names.get(id) ?? id
}

import { useQuery } from "@tanstack/react-query"
import type { ProviderSummary } from "@workspace/types"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

const index = (providers: ProviderSummary[]) => ({
    list: providers,
    ids: providers.map((provider) => provider.id),
    names: Object.fromEntries(
        providers.map((provider) => [provider.id, provider.display_name])
    ) as Record<string, string>,
    logos: Object.fromEntries(
        providers.map((provider) => [provider.id, provider.logo_url])
    ) as Record<string, string | null>,
})

const EMPTY = index([])

export function useProviders() {
    const providers = useQuery({
        queryKey: queryKeys.providers,
        queryFn: gatewayAdmin.providers,
        staleTime: Infinity,
        select: (collection) => index(collection.data),
    })
    return providers.data ?? EMPTY
}

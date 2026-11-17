import { useQuery } from "@tanstack/react-query"

import { PAGE_SIZE } from "@workspace/design-system"
import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

import { kindsOf, sourcesOf, type ActivityFilter } from "./activity-filters"

export function useActivity(filter: ActivityFilter, page: number) {
    const params = {
        kind: kindsOf(filter),
        source: sourcesOf(filter),
        page,
        size: PAGE_SIZE,
    }
    const activity = useQuery({
        queryKey: queryKeys.activity(params),
        queryFn: () => gatewayAdmin.activity(params),
        refetchInterval: LIVE,
        placeholderData: (previous) => previous,
    })
    const overview = useQuery({
        queryKey: queryKeys.overview,
        queryFn: gatewayAdmin.overview,
        refetchInterval: LIVE,
    })
    return {
        items: activity.data?.data ?? [],
        info: activity.data?.page,
        unreachable: activity.isError,
        overview: overview.data,
    }
}

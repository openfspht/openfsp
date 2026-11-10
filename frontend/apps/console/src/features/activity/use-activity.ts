import { useQuery } from "@tanstack/react-query"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

import { kindsOf, matches, type ActivityFilter } from "./activity-filters"

export function useActivity(filter: ActivityFilter) {
  const activity = useQuery({
    queryKey: queryKeys.activity(filter),
    queryFn: () => gatewayAdmin.activity(kindsOf(filter)),
    refetchInterval: LIVE,
  })
  const overview = useQuery({ queryKey: queryKeys.overview, queryFn: gatewayAdmin.overview, refetchInterval: LIVE })
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  return {
    items: (activity.data?.data ?? []).filter((item) => matches(filter, item)),
    loading: activity.isPending,
    unreachable: activity.isError,
    overview: overview.data,
    providerNames: Object.fromEntries((providers.data?.data ?? []).map((provider) => [provider.id, provider.display_name])),
  }
}

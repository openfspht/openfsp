import { useQuery } from "@tanstack/react-query"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

import { useProviderNames } from "@/features/providers/use-provider-names"

import { kindsOf, matches, type ActivityFilter } from "./activity-filters"

export function useActivity(filter: ActivityFilter) {
  const activity = useQuery({
    queryKey: queryKeys.activity(filter),
    queryFn: () => gatewayAdmin.activity(kindsOf(filter)),
    refetchInterval: LIVE,
  })
  const overview = useQuery({ queryKey: queryKeys.overview, queryFn: gatewayAdmin.overview, refetchInterval: LIVE })
  return {
    items: (activity.data?.data ?? []).filter((item) => matches(filter, item)),
    loading: activity.isPending,
    unreachable: activity.isError,
    overview: overview.data,
    providerNames: useProviderNames(),
  }
}

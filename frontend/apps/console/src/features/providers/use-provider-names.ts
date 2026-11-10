import { useQuery } from "@tanstack/react-query"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

export function useProviderNames(): Record<string, string> {
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  return Object.fromEntries((providers.data?.data ?? []).map((provider) => [provider.id, provider.display_name]))
}

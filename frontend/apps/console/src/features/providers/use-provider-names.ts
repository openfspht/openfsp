import { useQuery } from "@tanstack/react-query"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

function useProviderList() {
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  return providers.data?.data ?? []
}

export function useProviderNames(): Record<string, string> {
  return Object.fromEntries(useProviderList().map((provider) => [provider.id, provider.display_name]))
}

export function useProviderLogos(): Record<string, string | null> {
  return Object.fromEntries(useProviderList().map((provider) => [provider.id, provider.logo_url]))
}

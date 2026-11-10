import { useQuery } from "@tanstack/react-query"
import { ProvidersCard } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

export function ProvidersSection() {
  const providers = useQuery({
    queryKey: queryKeys.providers,
    queryFn: gatewayAdmin.providers,
  })
  return <ProvidersCard providers={providers.data?.data ?? []} />
}

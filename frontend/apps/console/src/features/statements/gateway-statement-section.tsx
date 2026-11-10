import { useQuery } from "@tanstack/react-query"
import { GatewayStatementCard } from "@workspace/design-system"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useProviderNames } from "@/features/providers/use-provider-names"

export function GatewayStatementSection() {
  const providers = useQuery({
    queryKey: queryKeys.providers,
    queryFn: gatewayAdmin.providers,
  })
  const ids = providers.data?.data.map((provider) => provider.id) ?? []
  const names = useProviderNames()
  const [chosen, setChosen] = useState<string>()
  const provider = chosen ?? ids[0]
  const statement = useQuery({
    queryKey: queryKeys.gatewayStatement(provider ?? ""),
    queryFn: () => gatewayAdmin.gatewayStatement(provider ?? ""),
    enabled: provider !== undefined,
    refetchInterval: LIVE,
  })
  if (provider === undefined) return null
  return (
    <GatewayStatementCard
      statement={statement.data?.data[0]}
      provider={provider}
      providers={ids}
      onProviderChange={setChosen}
      labelOf={(id) => names[id] ?? id}
      headless
    />
  )
}

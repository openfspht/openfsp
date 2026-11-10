import { useQuery } from "@tanstack/react-query"
import { GatewayStatementCard } from "@workspace/design-system"
import { SIMULATED_OPERATORS, type SimulatedOperator } from "@workspace/types"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"

export function GatewayStatementSection() {
  const [provider, setProvider] = useState<SimulatedOperator>("mock_alpha")
  const statement = useQuery({
    queryKey: queryKeys.gatewayStatement(provider),
    queryFn: () => gatewayAdmin.gatewayStatement(provider),
    refetchInterval: LIVE,
  })
  return (
    <GatewayStatementCard
      statement={statement.data?.data[0]}
      provider={provider}
      providers={SIMULATED_OPERATORS}
      onProviderChange={setProvider}
    />
  )
}

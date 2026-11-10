import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { ScopeName } from "@workspace/types"
import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

export function useKeys() {
  const queryClient = useQueryClient()
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: queryKeys.keys })
  const keys = useQuery({
    queryKey: queryKeys.keys,
    queryFn: gatewayAdmin.keys,
  })
  const issue = useMutation({
    mutationFn: ({
      principal,
      scopes,
    }: {
      principal: string
      scopes: ScopeName[]
    }) => gatewayAdmin.issueKey(principal, scopes),
    onSuccess: refresh,
  })
  const revoke = useMutation({
    mutationFn: gatewayAdmin.revokeKey,
    onSuccess: refresh,
  })
  return { keys, issue, revoke }
}

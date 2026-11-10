import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { ScopeName } from "@workspace/types"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys, type QueryParams } from "@/api/query-keys"

export function useKeys(params: QueryParams) {
  const queryClient = useQueryClient()
  const refresh = () =>
    Promise.all(
      [queryKeys.allKeys, queryKeys.overview].map((queryKey) =>
        queryClient.invalidateQueries({ queryKey })
      )
    )
  const keys = useQuery({
    queryKey: queryKeys.keys(params),
    queryFn: () => gatewayAdmin.keys(params),
    placeholderData: (previous) => previous,
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

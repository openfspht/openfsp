import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { adminSession } from "@/api/admin-session"
import { UnauthenticatedError } from "@/api/http"
import { queryKeys } from "@/api/query-keys"

export function useSession() {
  const queryClient = useQueryClient()
  const session = useQuery({
    queryKey: queryKeys.session,
    queryFn: adminSession.current,
    retry: false,
  })
  const signIn = useMutation({
    mutationFn: adminSession.signIn,
    onSuccess: () => queryClient.invalidateQueries(),
  })
  const signOut = useMutation({
    mutationFn: adminSession.signOut,
    onSuccess: () => queryClient.clear(),
  })
  return {
    pending: session.isPending,
    signedIn: session.isSuccess,
    rejected: signIn.error instanceof UnauthenticatedError,
    failed: signIn.isError && !(signIn.error instanceof UnauthenticatedError),
    signIn,
    signOut,
  }
}

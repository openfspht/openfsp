import { SignInCard } from "@workspace/design-system"

import { useSession } from "./use-session"

export function SessionGate({
  children,
}: {
  children: (signOut: () => void) => React.ReactNode
}) {
  const session = useSession()
  if (session.pending) return null
  if (!session.signedIn)
    return (
      <SignInCard
        onSubmit={session.signIn.mutate}
        busy={session.signIn.isPending}
        rejected={session.rejected}
        failed={session.failed}
      />
    )
  return children(() => session.signOut.mutate())
}

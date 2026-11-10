import { KeysCard } from "@workspace/design-system"

import { useKeys } from "./use-keys"

export function KeysSection() {
  const { keys, issue, revoke } = useKeys()
  return (
    <KeysCard
      headless
      keys={keys.data?.data ?? []}
      issuedSecret={issue.data?.secret}
      onIssue={(principal, scopes) => issue.mutate({ principal, scopes })}
      onRevoke={revoke.mutate}
      busy={issue.isPending || revoke.isPending}
    />
  )
}

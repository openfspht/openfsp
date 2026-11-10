import { Button, IssuedKeyNotice, IssueKeyCard, KeysTable, PageHeader, useCopy } from "@workspace/design-system"
import { Plus } from "lucide-react"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

import { useKeys } from "./use-keys"

export function KeysPage() {
  const { t } = useI18n()
  const copy = useCopy().keys
  const { keys, issue, revoke } = useKeys()
  const [issuing, setIssuing] = useState(false)
  return (
    <div className="space-y-8">
      <PageHeader
        title={t.keys.title}
        description={t.keys.description}
        actions={
          !issuing && (
            <Button
              size="sm"
              onClick={() => {
                issue.reset()
                setIssuing(true)
              }}
            >
              <Plus />
              {copy.issue}
            </Button>
          )
        }
      />
      {issue.data && <IssuedKeyNotice secret={issue.data.secret} onDismiss={() => issue.reset()} />}
      {issuing && (
        <IssueKeyCard
          onIssue={(principal, scopes) => issue.mutate({ principal, scopes }, { onSuccess: () => setIssuing(false) })}
          onCancel={() => setIssuing(false)}
          busy={issue.isPending}
        />
      )}
      <KeysTable keys={keys.data?.data ?? []} onRevoke={revoke.mutate} busy={revoke.isPending} />
    </div>
  )
}

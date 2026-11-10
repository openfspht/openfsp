import { SCOPES, type ApiKeySummary, type ScopeName } from "@workspace/types"
import { useState } from "react"

import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Tabular, Muted, Time } from "../atoms"
import { LabeledInput } from "../molecules"
import { useCopy } from "../copy"

const ALWAYS_GRANTED: ScopeName = "capabilities:read"

interface KeysCardProps {
  keys: ApiKeySummary[]
  issuedSecret?: string
  onIssue: (principal: string, scopes: ScopeName[]) => void
  onRevoke: (id: string) => void
  busy?: boolean
  headless?: boolean
}

export function KeysCard({ keys, issuedSecret, onIssue, onRevoke, busy, headless }: KeysCardProps) {
  const copy = useCopy().keys
  const [principal, setPrincipal] = useState("")
  const [scopes, setScopes] = useState<ScopeName[]>([ALWAYS_GRANTED, "payments:read", "payments:write"])
  const toggle = (scope: ScopeName) => setScopes((current) => (current.includes(scope) ? current.filter((it) => it !== scope) : [...current, scope]))

  return (
    <Card>
      {!headless && (
        <CardHeader>
          <CardTitle className="font-heading">{copy.title}</CardTitle>
          <CardDescription>{copy.description}</CardDescription>
        </CardHeader>
      )}
      <CardContent className="space-y-6">
        <div className="space-y-3">
          <LabeledInput id="principal" label={copy.principal} placeholder={copy.principalPlaceholder} value={principal} onChange={(event) => setPrincipal(event.target.value)} />
          <div className="flex flex-wrap gap-2">
            {SCOPES.map((scope) => (
              <Button key={scope} size="sm" variant={scopes.includes(scope) ? "default" : "outline"} disabled={scope === ALWAYS_GRANTED} onClick={() => toggle(scope)}>
                {scope}
              </Button>
            ))}
          </div>
          <Button disabled={busy || !principal.trim()} onClick={() => onIssue(principal.trim(), scopes)}>
            {copy.issue}
          </Button>
          {issuedSecret && (
            <div className="space-y-1 border border-accent p-3">
              <Muted className="block text-xs">{copy.copyNow}</Muted>
              <Tabular className="block break-all text-sm normal-case">{issuedSecret}</Tabular>
            </div>
          )}
        </div>
        <div className="space-y-3">
          {keys.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
          {keys.map((key) => (
            <div key={key.id} className="space-y-1 border-b pb-3 last:border-b-0">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium">{key.principal}</span>
                {key.revoked_at ? (
                  <Badge variant="destructive">{copy.revoked}</Badge>
                ) : (
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => onRevoke(key.id)}>
                    {copy.revoke}
                  </Button>
                )}
              </div>
              <Tabular className="block text-muted-foreground normal-case">{key.prefix}…</Tabular>
              <Muted className="block text-xs">
                <span className="block">{key.scopes.join(", ") || copy.noScope}</span>
                <span className="block">
                  {copy.lastUsed}: {key.last_used_at ? <Time value={key.last_used_at} withDate /> : copy.never}
                </span>
              </Muted>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

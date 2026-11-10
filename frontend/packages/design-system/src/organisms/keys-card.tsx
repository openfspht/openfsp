import { SCOPES, type ApiKeySummary, type ScopeName } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"
import { useState } from "react"

import { Badge, Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, Muted, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"
import { LabeledInput } from "../molecules"

const ALWAYS_GRANTED: ScopeName = "capabilities:read"
const COPIED_FOR_MS = 2000

interface KeysTableProps {
  keys: ApiKeySummary[]
  onRevoke: (id: string) => void
  busy?: boolean
}

export function KeysTable({ keys, onRevoke, busy }: KeysTableProps) {
  const copy = useCopy().keys
  const [confirming, setConfirming] = useState<string>()
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.principal}</TableHead>
              <TableHead>{copy.key}</TableHead>
              <TableHead>{copy.scopes}</TableHead>
              <TableHead>{copy.created}</TableHead>
              <TableHead>{copy.lastUsed}</TableHead>
              <TableHead>{copy.status}</TableHead>
              <TableHead className="text-right">{copy.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {keys.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                  {copy.empty}
                </TableCell>
              </TableRow>
            )}
            {keys.map((key) => (
              <TableRow key={key.id} className="align-top">
                <TableCell className="font-medium">{key.principal}</TableCell>
                <TableCell>
                  <Tabular className="text-muted-foreground">{key.prefix}…</Tabular>
                </TableCell>
                <TableCell className="max-w-64 whitespace-normal">
                  <span className="flex flex-wrap gap-1.5">
                    {key.scopes.length === 0 && <Muted>{copy.noScope}</Muted>}
                    {key.scopes.map((scope) => (
                      <Badge key={scope} variant="secondary" className="normal-case">
                        {scope}
                      </Badge>
                    ))}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  <Time value={key.created_at} />
                </TableCell>
                <TableCell className="text-muted-foreground">{key.last_used_at ? <Time value={key.last_used_at} /> : copy.never}</TableCell>
                <TableCell>
                  <Badge variant={key.revoked_at ? "destructive" : "outline"}>{key.revoked_at ? copy.revoked : copy.active}</Badge>
                </TableCell>
                <TableCell className="text-right">
                  {!key.revoked_at && confirming !== key.id && (
                    <Button size="xs" variant="outline" disabled={busy} onClick={() => setConfirming(key.id)}>
                      {copy.revoke}
                    </Button>
                  )}
                  {!key.revoked_at && confirming === key.id && (
                    <span className="inline-flex gap-2">
                      <Button size="xs" variant="destructive" disabled={busy} onClick={() => onRevoke(key.id)}>
                        {copy.confirmRevoke}
                      </Button>
                      <Button size="xs" variant="ghost" onClick={() => setConfirming(undefined)}>
                        {copy.cancel}
                      </Button>
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

interface IssueKeyCardProps {
  onIssue: (principal: string, scopes: ScopeName[]) => void
  onCancel: () => void
  busy?: boolean
}

export function IssueKeyCard({ onIssue, onCancel, busy }: IssueKeyCardProps) {
  const copy = useCopy().keys
  const [principal, setPrincipal] = useState("")
  const [scopes, setScopes] = useState<ScopeName[]>([ALWAYS_GRANTED, "payments:read", "payments:write"])
  const toggle = (scope: ScopeName) => setScopes((current) => (current.includes(scope) ? current.filter((it) => it !== scope) : [...current, scope]))
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.issue}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            onIssue(principal.trim(), scopes)
          }}
        >
          <div className="max-w-md">
            <LabeledInput id="principal" label={copy.principal} placeholder={copy.principalPlaceholder} value={principal} onChange={(event) => setPrincipal(event.target.value)} />
          </div>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">{copy.scopes}</legend>
            <div className="flex flex-wrap gap-2">
              {SCOPES.map((scope) => (
                <Button
                  key={scope}
                  type="button"
                  size="xs"
                  className="normal-case tracking-normal"
                  variant={scopes.includes(scope) ? "default" : "outline"}
                  aria-pressed={scopes.includes(scope)}
                  disabled={scope === ALWAYS_GRANTED}
                  onClick={() => toggle(scope)}
                >
                  {scope}
                </Button>
              ))}
            </div>
          </fieldset>
          <div className="flex gap-2">
            <Button type="submit" disabled={busy || !principal.trim()}>
              {copy.issue}
            </Button>
            <Button type="button" variant="ghost" onClick={onCancel}>
              {copy.cancel}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

export function IssuedKeyNotice({ secret, onDismiss }: { secret: string; onDismiss: () => void }) {
  const copy = useCopy().keys
  const [copied, setCopied] = useState(false)
  return (
    <Card className="ring-accent">
      <CardHeader>
        <CardTitle className="font-heading">{copy.issuedTitle}</CardTitle>
        <CardDescription>{copy.copyNow}</CardDescription>
        <CardAction>
          <Button size="xs" variant="ghost" onClick={onDismiss}>
            {copy.dismiss}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-3">
        <Tabular className="break-all bg-muted px-3 py-2">{secret}</Tabular>
        <Button
          size="sm"
          variant="outline"
          onClick={() =>
            void navigator.clipboard.writeText(secret).then(() => {
              setCopied(true)
              setTimeout(() => setCopied(false), COPIED_FOR_MS)
            })
          }
        >
          {copied ? copy.copied : copy.copy}
        </Button>
      </CardContent>
    </Card>
  )
}

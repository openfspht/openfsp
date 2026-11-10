import { SCOPES, type ApiKeySummary, type ScopeName } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"
import { useState } from "react"

import { Badge, Button, Card, CardContent, Muted, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"
import { LabeledInput, SortableHead, type SortState, TableEmptyRow, CopyButton, ToggleChips } from "../molecules"

const ALWAYS_GRANTED: ScopeName = "capabilities:read"

interface KeysTableProps {
  keys: ApiKeySummary[]
  onRevoke: (id: string) => void
  busy?: boolean
  sort?: SortState
  onSort?: (key: string) => void
}

export function KeysTable({ keys, onRevoke, busy, sort, onSort }: KeysTableProps) {
  const copy = useCopy().keys
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <SortableHead label={copy.principal} sortKey="principal" sort={sort} onSort={onSort} />
              <TableHead>{copy.key}</TableHead>
              <TableHead>{copy.scopes}</TableHead>
              <SortableHead label={copy.created} sortKey="created" sort={sort} onSort={onSort} />
              <SortableHead label={copy.lastUsed} sortKey="last_used" sort={sort} onSort={onSort} />
              <SortableHead label={copy.status} sortKey="status" sort={sort} onSort={onSort} />
              <TableHead className="text-right">{copy.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {keys.length === 0 && (
              <TableEmptyRow columns={7}>{copy.empty}</TableEmptyRow>
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
                  {!key.revoked_at && (
                    <Button size="xs" variant="outline" disabled={busy} onClick={() => onRevoke(key.id)}>
                      {copy.revoke}
                    </Button>
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

interface IssueKeyFormProps {
  onIssue: (principal: string, scopes: ScopeName[]) => void
  onCancel: () => void
  busy?: boolean
}

export function IssueKeyForm({ onIssue, onCancel, busy }: IssueKeyFormProps) {
  const copy = useCopy().keys
  const [principal, setPrincipal] = useState("")
  const [scopes, setScopes] = useState<ScopeName[]>([ALWAYS_GRANTED, "payments:read", "payments:write"])
  return (
    <form
      className="space-y-5"
      onSubmit={(event) => {
        event.preventDefault()
        onIssue(principal.trim(), scopes)
      }}
    >
      <LabeledInput id="principal" label={copy.principal} placeholder={copy.principalPlaceholder} value={principal} onChange={(event) => setPrincipal(event.target.value)} />
      <ToggleChips label={copy.scopes} options={SCOPES} value={scopes} onChange={setScopes} locked={[ALWAYS_GRANTED]} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          {copy.cancel}
        </Button>
        <Button type="submit" disabled={busy || !principal.trim()}>
          {copy.issue}
        </Button>
      </div>
    </form>
  )
}

export function IssuedSecret({ secret, onDone }: { secret: string; onDone: () => void }) {
  const copy = useCopy().keys
  return (
    <div className="space-y-5">
      <Tabular className="block break-all bg-muted px-3 py-3">{secret}</Tabular>
      <div className="flex justify-end gap-2">
        <CopyButton text={secret} />
        <Button onClick={onDone}>{copy.dismiss}</Button>
      </div>
    </div>
  )
}

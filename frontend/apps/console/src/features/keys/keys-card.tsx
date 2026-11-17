import {
    ActionError,
    Badge,
    Button,
    Card,
    CardContent,
    CopyButton,
    LabeledInput,
    Muted,
    SortableHead,
    type SortState,
    TableEmptyRow,
    Tabular,
    Time,
    ToggleChips,
} from "@workspace/design-system"
import { SCOPES, type ApiKeySummary, type ScopeName } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

const ALWAYS_GRANTED: ScopeName = "capabilities:read"

interface KeysTableProps {
    keys: ApiKeySummary[]
    onRevoke: (id: string) => void
    busy?: boolean
    sort?: SortState
    onSort?: (key: string) => void
}

type KeyState = "active" | "expired" | "revoked"

const stateBadge: Record<KeyState, "outline" | "secondary" | "destructive"> = {
    active: "outline",
    expired: "secondary",
    revoked: "destructive",
}

const stateOf = (key: ApiKeySummary, now: number): KeyState =>
    key.revoked_at
        ? "revoked"
        : key.expires_at && new Date(key.expires_at).getTime() <= now
          ? "expired"
          : "active"

export function KeysTable({ keys, onRevoke, busy, sort, onSort }: KeysTableProps) {
    const copy = useI18n().t.keys
    const [now] = useState(() => Date.now())
    return (
        <Card>
            <CardContent>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <SortableHead
                                label={copy.principal}
                                sortKey="principal"
                                sort={sort}
                                onSort={onSort}
                            />
                            <TableHead>{copy.key}</TableHead>
                            <TableHead>{copy.scopes}</TableHead>
                            <SortableHead
                                label={copy.created}
                                sortKey="created"
                                sort={sort}
                                onSort={onSort}
                            />
                            <SortableHead
                                label={copy.lastUsed}
                                sortKey="last_used"
                                sort={sort}
                                onSort={onSort}
                            />
                            <SortableHead
                                label={copy.status}
                                sortKey="status"
                                sort={sort}
                                onSort={onSort}
                            />
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
                                    <Tabular className="text-muted-foreground">
                                        {key.prefix}…
                                    </Tabular>
                                </TableCell>
                                <TableCell className="max-w-64 whitespace-normal">
                                    <span className="flex flex-wrap gap-1.5">
                                        {key.scopes.length === 0 && <Muted>{copy.noScope}</Muted>}
                                        {key.scopes.map((scope) => (
                                            <Badge
                                                key={scope}
                                                variant="secondary"
                                                className="normal-case"
                                            >
                                                {scope}
                                            </Badge>
                                        ))}
                                    </span>
                                </TableCell>
                                <TableCell className="text-muted-foreground">
                                    <Time value={key.created_at} />
                                </TableCell>
                                <TableCell className="text-muted-foreground">
                                    {key.last_used_at ? (
                                        <Time value={key.last_used_at} />
                                    ) : (
                                        copy.never
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Badge variant={stateBadge[stateOf(key, now)]}>
                                        {copy[stateOf(key, now)]}
                                    </Badge>
                                </TableCell>
                                <TableCell className="text-right">
                                    {!key.revoked_at && (
                                        <Button
                                            size="xs"
                                            variant="outline"
                                            disabled={busy}
                                            onClick={() => onRevoke(key.id)}
                                        >
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
    error?: Error | null
}

export function IssueKeyForm({ onIssue, onCancel, busy, error }: IssueKeyFormProps) {
    const copy = useI18n().t.keys
    const [principal, setPrincipal] = useState("")
    const [scopes, setScopes] = useState<ScopeName[]>([
        ALWAYS_GRANTED,
        "payments:read",
        "payments:write",
    ])
    return (
        <form
            className="space-y-5"
            onSubmit={(event) => {
                event.preventDefault()
                onIssue(principal.trim(), scopes)
            }}
        >
            <LabeledInput
                id="principal"
                label={copy.principal}
                placeholder={copy.principalPlaceholder}
                value={principal}
                onChange={(event) => setPrincipal(event.target.value)}
            />
            <ToggleChips
                label={copy.scopes}
                options={SCOPES}
                value={scopes}
                onChange={setScopes}
                locked={[ALWAYS_GRANTED]}
            />
            <ActionError error={error} />
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
    const copy = useI18n().t.keys
    return (
        <div className="space-y-5">
            <Tabular className="block bg-muted px-3 py-3 break-all">{secret}</Tabular>
            <div className="flex justify-end gap-2">
                <CopyButton text={secret} />
                <Button onClick={onDone}>{copy.dismiss}</Button>
            </div>
        </div>
    )
}

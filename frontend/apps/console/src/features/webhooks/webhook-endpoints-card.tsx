import {
    ActionError,
    Badge,
    Button,
    Card,
    CardAction,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    ErrorText,
    Field,
    LabeledInput,
    Muted,
    SortableHead,
    type SortState,
    TableEmptyRow,
    Tabular,
    Time,
    ToggleChips,
} from "@workspace/design-system"
import {
    EVENT_TYPES,
    type EventType,
    type SigningKeys,
    type SigningKeyView,
    type WebhookEndpoint,
} from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { useState } from "react"

import { AccountField } from "@/features/accounts/account-select"
import { useI18n } from "@/i18n/i18n-provider"

interface WebhookEndpointsCardProps {
    endpoints: WebhookEndpoint[]
    onEnable: (name: string) => void
    onRemove: (name: string) => void
    busy?: boolean
    sort?: SortState
    onSort?: (key: string) => void
}

export function WebhookEndpointsCard({
    endpoints,
    onEnable,
    onRemove,
    busy,
    sort,
    onSort,
}: WebhookEndpointsCardProps) {
    const copy = useI18n().t.webhooks
    return (
        <Card>
            <CardContent>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <SortableHead
                                label={copy.name}
                                sortKey="name"
                                sort={sort}
                                onSort={onSort}
                            />
                            <SortableHead
                                label={copy.url}
                                sortKey="url"
                                sort={sort}
                                onSort={onSort}
                            />
                            <TableHead>{copy.events}</TableHead>
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
                        {endpoints.length === 0 && (
                            <TableEmptyRow columns={5}>{copy.empty}</TableEmptyRow>
                        )}
                        {endpoints.map((endpoint) => (
                            <TableRow key={endpoint.name} className="align-top">
                                <TableCell>
                                    <span className="block font-medium">{endpoint.name}</span>
                                    {!endpoint.managed && (
                                        <Muted className="text-sm">{copy.configured}</Muted>
                                    )}
                                </TableCell>
                                <TableCell className="max-w-72 break-all whitespace-normal">
                                    <Tabular className="text-muted-foreground">
                                        {endpoint.url}
                                    </Tabular>
                                </TableCell>
                                <TableCell className="max-w-64 whitespace-normal">
                                    <span className="flex flex-wrap gap-1.5">
                                        {endpoint.events.map((event) => (
                                            <Badge
                                                key={event}
                                                variant="secondary"
                                                className="normal-case"
                                            >
                                                {event}
                                            </Badge>
                                        ))}
                                    </span>
                                </TableCell>
                                <TableCell className="whitespace-normal">
                                    {endpoint.disabled_at ? (
                                        <span className="space-y-1">
                                            <Badge variant="destructive">{copy.disabled}</Badge>
                                            <ErrorText className="text-sm">
                                                <Time value={endpoint.disabled_at} />
                                                {endpoint.reason && (
                                                    <span className="block">{endpoint.reason}</span>
                                                )}
                                            </ErrorText>
                                        </span>
                                    ) : (
                                        <Badge variant="outline">
                                            {endpoint.enabled ? copy.enabled : copy.off}
                                        </Badge>
                                    )}
                                    {endpoint.pending > 0 && (
                                        <Muted className="block text-sm">
                                            {copy.pending(endpoint.pending)}
                                        </Muted>
                                    )}
                                </TableCell>
                                <TableCell className="text-right">
                                    <span className="inline-flex flex-wrap justify-end gap-2">
                                        {endpoint.disabled_at && (
                                            <Button
                                                size="xs"
                                                variant="outline"
                                                disabled={busy}
                                                onClick={() => onEnable(endpoint.name)}
                                            >
                                                {copy.enable}
                                            </Button>
                                        )}
                                        {endpoint.managed && (
                                            <Button
                                                size="xs"
                                                variant="outline"
                                                disabled={busy}
                                                onClick={() => onRemove(endpoint.name)}
                                            >
                                                {copy.remove}
                                            </Button>
                                        )}
                                    </span>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    )
}

interface AddWebhookFormProps {
    account: string
    onAdd: (webhook: { account: string; name: string; url: string; events: EventType[] }) => void
    onCancel: () => void
    error?: Error | null
    busy?: boolean
}

export function AddWebhookForm({ account, onAdd, onCancel, error, busy }: AddWebhookFormProps) {
    const copy = useI18n().t.webhooks
    const [chosen, setChosen] = useState(account)
    const [name, setName] = useState("")
    const [url, setUrl] = useState("")
    const [events, setEvents] = useState<EventType[]>(["payment.succeeded", "payment.failed"])
    return (
        <form
            className="space-y-5"
            onSubmit={(event) => {
                event.preventDefault()
                onAdd({ account: chosen, name: name.trim(), url: url.trim(), events })
            }}
        >
            <AccountField value={chosen} onChange={setChosen} />
            <LabeledInput
                id="webhook-name"
                label={copy.name}
                hint={copy.nameHint}
                placeholder={copy.namePlaceholder}
                value={name}
                onChange={(event) => setName(event.target.value)}
            />
            <LabeledInput
                id="webhook-url"
                label={copy.url}
                type="url"
                placeholder={copy.urlPlaceholder}
                value={url}
                onChange={(event) => setUrl(event.target.value)}
            />
            <ToggleChips
                label={copy.events}
                options={EVENT_TYPES}
                value={events}
                onChange={setEvents}
            />
            <ActionError error={error} />
            <div className="flex justify-end gap-2">
                <Button type="button" variant="ghost" onClick={onCancel}>
                    {copy.cancel}
                </Button>
                <Button
                    type="submit"
                    disabled={busy || !chosen || !name.trim() || !url.trim() || events.length === 0}
                >
                    {copy.add}
                </Button>
            </div>
        </form>
    )
}

interface SigningKeyCardProps {
    keys?: SigningKeys
    onRotate: () => void
    busy?: boolean
}

type KeyPhase = "signing" | "next" | "retired"

const phaseOf = (key: SigningKeyView, now: number): KeyPhase =>
    key.status === "retired"
        ? "retired"
        : new Date(key.activates_at).getTime() > now
          ? "next"
          : "signing"

const phaseBadge: Record<KeyPhase, "default" | "outline" | "secondary"> = {
    signing: "default",
    next: "outline",
    retired: "secondary",
}

export function SigningKeyCard({ keys, onRotate, busy }: SigningKeyCardProps) {
    const copy = useI18n().t.webhooks
    const [now] = useState(() => Date.now())
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{copy.rotateTitle}</CardTitle>
                <CardDescription>{copy.rotateDescription}</CardDescription>
                <CardAction>
                    <Button size="sm" variant="outline" disabled={busy} onClick={onRotate}>
                        {copy.rotate}
                    </Button>
                </CardAction>
            </CardHeader>
            <CardContent className="space-y-4">
                <dl className="space-y-2">
                    <Field label={copy.rotation}>
                        {keys?.rotate_every_minutes
                            ? copy.every(keys.rotate_every_minutes)
                            : copy.rotationOff}
                    </Field>
                    {keys?.next_rotation_at && (
                        <Field label={copy.nextRotation}>
                            <Time value={keys.next_rotation_at} />
                        </Field>
                    )}
                </dl>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>{copy.keyId}</TableHead>
                            <TableHead>{copy.status}</TableHead>
                            <TableHead>{copy.signsFrom}</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody striped>
                        {(keys?.keys ?? []).map((key) => {
                            const phase = phaseOf(key, now)
                            return (
                                <TableRow key={key.kid}>
                                    <TableCell>
                                        <Tabular>{key.kid}</Tabular>
                                    </TableCell>
                                    <TableCell>
                                        <Badge variant={phaseBadge[phase]}>
                                            {copy.phases[phase]}
                                        </Badge>
                                    </TableCell>
                                    <TableCell className="text-muted-foreground">
                                        <Time value={key.activates_at} />
                                    </TableCell>
                                </TableRow>
                            )
                        })}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    )
}

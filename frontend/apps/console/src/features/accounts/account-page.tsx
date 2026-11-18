import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
    ActionError,
    Badge,
    Button,
    Card,
    CardContent,
    ConfirmModal,
    CopyButton,
    LabeledInput,
    Modal,
    Muted,
    PageHeader,
    TableEmptyRow,
    Tabular,
    Time,
} from "@workspace/design-system"
import type { AccountProvider } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { useState } from "react"
import { useParams } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

import { useAccountNames } from "./use-accounts"

const sourceBadge: Record<AccountProvider["source"], "outline" | "secondary" | "destructive"> = {
    account: "outline",
    platform: "secondary",
    none: "destructive",
}

export function AccountPage() {
    const copy = useI18n().t.accounts
    const account = useParams().id ?? ""
    const nameOf = useAccountNames()
    const queryClient = useQueryClient()
    const [editing, setEditing] = useState<AccountProvider>()
    const [removing, setRemoving] = useState<AccountProvider>()
    const providers = useQuery({
        queryKey: queryKeys.accountProviders(account),
        queryFn: () => gatewayAdmin.accountProviders(account),
    })
    const refresh = () =>
        queryClient.invalidateQueries({ queryKey: queryKeys.accountProviders(account) })
    const save = useMutation({
        mutationFn: ({
            provider,
            credentials,
        }: {
            provider: string
            credentials: Record<string, string>
        }) => gatewayAdmin.saveCredentials(account, provider, credentials),
        onSuccess: () => {
            setEditing(undefined)
            return refresh()
        },
    })
    const removal = useMutation({
        mutationFn: (provider: string) => gatewayAdmin.removeCredentials(account, provider),
        onSuccess: () => {
            setRemoving(undefined)
            return refresh()
        },
    })
    const all = providers.data?.data ?? []
    return (
        <div className="space-y-8">
            <PageHeader title={nameOf(account)} description={copy.providersDescription} />
            <Card>
                <CardContent>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>{copy.provider}</TableHead>
                                <TableHead>{copy.credentials}</TableHead>
                                <TableHead>{copy.updated}</TableHead>
                                <TableHead>{copy.addresses}</TableHead>
                                <TableHead className="text-right">{copy.actions}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody striped>
                            {all.length === 0 && (
                                <TableEmptyRow columns={5}>{copy.noProvider}</TableEmptyRow>
                            )}
                            {all.map((provider) => (
                                <TableRow key={provider.provider} className="align-top">
                                    <TableCell className="font-medium">
                                        {provider.display_name}
                                    </TableCell>
                                    <TableCell>
                                        <Badge variant={sourceBadge[provider.source]}>
                                            {copy.sources[provider.source]}
                                        </Badge>
                                    </TableCell>
                                    <TableCell className="text-muted-foreground">
                                        {provider.updated_at ? (
                                            <Time value={provider.updated_at} />
                                        ) : null}
                                    </TableCell>
                                    <TableCell className="max-w-96 whitespace-normal">
                                        {provider.notification_url && (
                                            <Address
                                                label={copy.notificationUrl}
                                                value={provider.notification_url}
                                            />
                                        )}
                                        {provider.return_url && (
                                            <Address
                                                label={copy.returnUrl}
                                                value={provider.return_url}
                                            />
                                        )}
                                    </TableCell>
                                    <TableCell className="space-x-2 text-right">
                                        {provider.accepts_account_credentials && (
                                            <Button
                                                size="xs"
                                                variant="outline"
                                                onClick={() => {
                                                    save.reset()
                                                    setEditing(provider)
                                                }}
                                            >
                                                {provider.source === "account"
                                                    ? copy.replace
                                                    : copy.set}
                                            </Button>
                                        )}
                                        {provider.source === "account" && (
                                            <Button
                                                size="xs"
                                                variant="outline"
                                                onClick={() => {
                                                    removal.reset()
                                                    setRemoving(provider)
                                                }}
                                            >
                                                {copy.remove}
                                            </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>
            <Modal
                open={editing !== undefined}
                onClose={() => setEditing(undefined)}
                title={editing?.display_name ?? ""}
                description={copy.credentialsDescription}
            >
                {editing && (
                    <CredentialsForm
                        names={editing.credential_names}
                        onSave={(credentials) =>
                            save.mutate({ provider: editing.provider, credentials })
                        }
                        onCancel={() => setEditing(undefined)}
                        busy={save.isPending}
                        error={save.error}
                    />
                )}
            </Modal>
            <ConfirmModal
                open={removing !== undefined}
                onClose={() => setRemoving(undefined)}
                onConfirm={() => removing && removal.mutate(removing.provider)}
                title={copy.removeTitle}
                description={copy.removeDescription(removing?.display_name ?? "")}
                confirmLabel={copy.confirmRemove}
                destructive
                busy={removal.isPending}
                error={removal.error}
            />
        </div>
    )
}

function Address({ label, value }: { label: string; value: string }) {
    return (
        <div className="mb-2 flex items-center gap-2">
            <Muted>{label}</Muted>
            <Tabular className="break-all">{value}</Tabular>
            <CopyButton text={value} />
        </div>
    )
}

function CredentialsForm({
    names,
    onSave,
    onCancel,
    busy,
    error,
}: {
    names: string[]
    onSave: (credentials: Record<string, string>) => void
    onCancel: () => void
    busy?: boolean
    error?: Error | null
}) {
    const copy = useI18n().t.accounts
    const [values, setValues] = useState<Record<string, string>>({})
    const complete = names.every((name) => (values[name] ?? "").trim() !== "")
    return (
        <form
            className="space-y-5"
            autoComplete="off"
            onSubmit={(event) => {
                event.preventDefault()
                onSave(Object.fromEntries(names.map((name) => [name, values[name]?.trim() ?? ""])))
            }}
        >
            {names.map((name) => (
                <LabeledInput
                    key={name}
                    id={`credential-${name}`}
                    label={name}
                    type="password"
                    autoComplete="off"
                    value={values[name] ?? ""}
                    onChange={(event) => setValues({ ...values, [name]: event.target.value })}
                />
            ))}
            <ActionError error={error} />
            <div className="flex justify-end gap-2">
                <Button type="button" variant="ghost" onClick={onCancel}>
                    {copy.cancel}
                </Button>
                <Button type="submit" disabled={busy || !complete}>
                    {copy.save}
                </Button>
            </div>
        </form>
    )
}

import {
    ActionError,
    Badge,
    Button,
    Card,
    CardContent,
    LabeledInput,
    ModalActions,
    Modal,
    PageHeader,
    TableEmptyRow,
    Tabular,
    Time,
} from "@workspace/design-system"
import type { MerchantAccount } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { Plus } from "lucide-react"
import { useState } from "react"
import { Link } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

import { useAccounts, useOpenAccount } from "./use-accounts"

export function AccountsPage() {
    const copy = useI18n().t.accounts
    const accounts = useAccounts()
    const open = useOpenAccount()
    const [opening, setOpening] = useState(false)
    return (
        <div className="space-y-8">
            <PageHeader
                title={copy.title}
                description={copy.description}
                actions={
                    <Button
                        size="sm"
                        onClick={() => {
                            open.reset()
                            setOpening(true)
                        }}
                    >
                        <Plus />
                        {copy.open}
                    </Button>
                }
            />
            <AccountsTable accounts={accounts.data ?? []} />
            <Modal
                open={opening}
                onClose={() => setOpening(false)}
                title={copy.open}
                description={copy.openDescription}
            >
                <OpenAccountForm
                    onOpen={(name) => open.mutate(name, { onSuccess: () => setOpening(false) })}
                    onCancel={() => setOpening(false)}
                    busy={open.isPending}
                    error={open.error}
                />
            </Modal>
        </div>
    )
}

function AccountsTable({ accounts }: { accounts: MerchantAccount[] }) {
    const copy = useI18n().t.accounts
    const [now] = useState(() => Date.now())
    return (
        <Card>
            <CardContent>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>{copy.name}</TableHead>
                            <TableHead>{copy.identifier}</TableHead>
                            <TableHead>{copy.created}</TableHead>
                            <TableHead>{copy.status}</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody striped>
                        {accounts.length === 0 && (
                            <TableEmptyRow columns={4}>{copy.empty}</TableEmptyRow>
                        )}
                        {accounts.map((account) => {
                            const expired =
                                account.expires_at !== undefined &&
                                new Date(account.expires_at).getTime() <= now
                            return (
                                <TableRow key={account.id}>
                                    <TableCell className="font-medium">
                                        <Link
                                            to={`/accounts/${account.id}`}
                                            className="outline-none hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                                        >
                                            {account.name}
                                        </Link>
                                    </TableCell>
                                    <TableCell>
                                        <Tabular className="text-muted-foreground">
                                            {account.id}
                                        </Tabular>
                                    </TableCell>
                                    <TableCell className="text-muted-foreground">
                                        <Time value={account.created_at} />
                                    </TableCell>
                                    <TableCell>
                                        <Badge variant={expired ? "secondary" : "outline"}>
                                            {expired
                                                ? copy.expired
                                                : account.expires_at
                                                  ? copy.temporary
                                                  : copy.active}
                                        </Badge>
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

function OpenAccountForm({
    onOpen,
    onCancel,
    busy,
    error,
}: {
    onOpen: (name: string) => void
    onCancel: () => void
    busy?: boolean
    error?: Error | null
}) {
    const copy = useI18n().t.accounts
    const [name, setName] = useState("")
    return (
        <form
            className="space-y-5"
            onSubmit={(event) => {
                event.preventDefault()
                onOpen(name.trim())
            }}
        >
            <LabeledInput
                id="account-name"
                label={copy.name}
                hint={copy.nameHint}
                placeholder={copy.namePlaceholder}
                value={name}
                onChange={(event) => setName(event.target.value)}
            />
            <ActionError error={error} />
            <ModalActions
                onCancel={onCancel}
                submitLabel={copy.open}
                disabled={busy || !name.trim()}
            />
        </form>
    )
}

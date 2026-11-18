import { Label, OptionSelect } from "@workspace/design-system"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

import { useAccountNames, useAccounts } from "./use-accounts"

const EVERY = "all"

export function AccountFilter({
    value,
    onChange,
}: {
    value: string
    onChange: (account: string) => void
}) {
    const copy = useI18n().t.accounts
    const nameOf = useAccountNames()
    const ids = (useAccounts().data ?? []).map((account) => account.id)
    return (
        <OptionSelect
            label={copy.filter}
            value={value || EVERY}
            options={[EVERY, ...ids]}
            labelOf={(id) => `${copy.filter}: ${id === EVERY ? copy.every : nameOf(id)}`}
            onChange={(id) => onChange(id === EVERY ? "" : id)}
        />
    )
}

export function AccountField({
    value,
    onChange,
}: {
    value: string
    onChange: (account: string) => void
}) {
    const copy = useI18n().t.accounts
    const [now] = useState(() => Date.now())
    const accounts = (useAccounts().data ?? []).filter(
        (account) => !account.expires_at || new Date(account.expires_at).getTime() > now
    )
    const nameOf = new Map(accounts.map((account) => [account.id, account.name]))
    return (
        <div className="space-y-2">
            <Label>{copy.account}</Label>
            <OptionSelect
                label={copy.account}
                className="w-full"
                value={value}
                options={accounts.map((account) => account.id)}
                labelOf={(account) => nameOf.get(account) ?? copy.choose}
                onChange={onChange}
            />
        </div>
    )
}

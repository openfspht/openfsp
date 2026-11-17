import { useQuery } from "@tanstack/react-query"
import {
    formatDate,
    formatDateTime,
    formatMoney,
    PageHeader,
    useCopy,
} from "@workspace/design-system"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useProviders } from "@/features/providers/use-providers"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"

import { GatewayStatementCard } from "./gateway-statement-card"

export function StatementsPage() {
    const { t } = useI18n()
    const copy = useCopy()
    const { ids, names } = useProviders()
    const [chosen, setChosen] = useState<string>()
    const provider = chosen ?? ids[0]
    const statement = useQuery({
        queryKey: queryKeys.gatewayStatement(provider ?? ""),
        queryFn: () => gatewayAdmin.gatewayStatement(provider ?? ""),
        enabled: provider !== undefined,
        refetchInterval: LIVE,
    })
    const current = statement.data?.data[0]
    const labels = t.gatewayStatement
    const providerName = provider ? (names[provider] ?? provider) : ""
    const table = {
        title: `${t.statements.title} ${providerName} ${current ? formatDate(current.business_date, copy.locale) : ""}`.trim(),
        filename: `statement-${provider ?? "gateway"}`,
        columns: [labels.reference, t.paymentsTable.status, labels.amount, labels.settledAt],
        empty: !current?.payments.length,
        rows: () =>
            (current?.payments ?? []).map((line) => [
                line.reference,
                copy.status[line.status],
                formatMoney(line.amount, copy.locale),
                formatDateTime(line.completed_at, copy.locale),
            ]),
    }
    return (
        <div className="max-w-3xl">
            <PageHeader
                title={t.statements.title}
                description={t.statements.description}
                actions={<TableExport table={table} />}
            />
            {provider !== undefined && (
                <GatewayStatementCard
                    statement={current}
                    provider={provider}
                    providers={ids}
                    onProviderChange={setChosen}
                    labelOf={(id) => names[id] ?? id}
                />
            )}
        </div>
    )
}

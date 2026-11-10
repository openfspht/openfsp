import { useQuery } from "@tanstack/react-query"
import { formatDateTime, formatMoney, GatewayStatementCard, PageHeader, useCopy } from "@workspace/design-system"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useProviderNames } from "@/features/providers/use-provider-names"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"

export function StatementsPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const names = useProviderNames()
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  const ids = providers.data?.data.map((provider) => provider.id) ?? []
  const [chosen, setChosen] = useState<string>()
  const provider = chosen ?? ids[0]
  const statement = useQuery({
    queryKey: queryKeys.gatewayStatement(provider ?? ""),
    queryFn: () => gatewayAdmin.gatewayStatement(provider ?? ""),
    enabled: provider !== undefined,
    refetchInterval: LIVE,
  })
  const current = statement.data?.data[0]
  const labels = copy.gatewayStatement
  const providerName = provider ? (names[provider] ?? provider) : ""
  const table = {
    title: `${t.statements.title} ${providerName} ${current?.business_date ?? ""}`.trim(),
    filename: `statement-${provider ?? "gateway"}`,
    columns: [labels.reference, copy.paymentsTable.status, labels.amount, labels.settledAt],
    rows: (current?.payments ?? []).map((line) => [
      line.reference,
      copy.status[line.status],
      formatMoney(line.amount, copy.locale),
      formatDateTime(line.completed_at, copy.locale),
    ]),
  }
  return (
    <div className="max-w-3xl">
      <PageHeader title={t.statements.title} description={t.statements.description} actions={<TableExport table={table} />} />
      {provider !== undefined && (
        <GatewayStatementCard statement={current} provider={provider} providers={ids} onProviderChange={setChosen} labelOf={(id) => names[id] ?? id} headless />
      )}
    </div>
  )
}

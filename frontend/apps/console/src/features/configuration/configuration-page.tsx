import { useQuery } from "@tanstack/react-query"
import { JsonPanel, PageHeader } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

export function ConfigurationPage() {
  const { t } = useI18n()
  const shown = useQuery({ queryKey: queryKeys.configuration, queryFn: gatewayAdmin.configuration })
  return (
    <>
      <PageHeader title={t.configuration.title} description={t.configuration.description} />
      <div className="grid gap-8 lg:grid-cols-2">
        <JsonPanel title={t.configuration.discovery} description={t.configuration.discoveryPath} value={shown.data?.configuration} />
        <JsonPanel title={t.configuration.capabilities} description={t.configuration.capabilitiesPath} value={shown.data?.capabilities} />
      </div>
    </>
  )
}

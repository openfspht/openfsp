import { useQuery } from "@tanstack/react-query"
import { PageHeader, ProvidersCard } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

export function ProvidersPage() {
  const { t } = useI18n()
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  return (
    <div className="max-w-3xl">
      <PageHeader title={t.providers.title} description={t.providers.description} />
      <ProvidersCard headless providers={providers.data?.data ?? []} />
    </div>
  )
}

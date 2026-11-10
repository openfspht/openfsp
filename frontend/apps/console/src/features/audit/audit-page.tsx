import { useQuery } from "@tanstack/react-query"
import { AuditTable, PageHeader } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

export function AuditPage() {
  const { t } = useI18n()
  const audit = useQuery({ queryKey: queryKeys.audit, queryFn: gatewayAdmin.audit, refetchInterval: LIVE })
  return (
    <>
      <PageHeader title={t.audit.title} description={t.audit.description} />
      <AuditTable headless records={audit.data?.data ?? []} />
    </>
  )
}

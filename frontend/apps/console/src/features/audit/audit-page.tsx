import { useQuery } from "@tanstack/react-query"
import { AuditTable, formatDateTime, PageHeader, useCopy } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"

export function AuditPage() {
  const { t } = useI18n()
  const audit = useQuery({ queryKey: queryKeys.audit, queryFn: gatewayAdmin.audit, refetchInterval: LIVE })
  const copy = useCopy()
  const labels = copy.audit
  const records = audit.data?.data ?? []
  const table = {
    title: t.audit.title,
    filename: "audit",
    columns: [labels.when, labels.kind, labels.operation, labels.principal, labels.result],
    rows: records.map((record) => [
      formatDateTime(record.recorded_at, copy.locale),
      labels.kinds[record.kind] ?? record.kind,
      record.operation,
      record.lookup_prefix ?? record.principal_id ?? "",
      record.result,
    ]),
  }
  return (
    <>
      <PageHeader title={t.audit.title} description={t.audit.description} actions={<TableExport table={table} />} />
      <AuditTable headless records={records} />
    </>
  )
}

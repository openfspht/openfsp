import { useQuery } from "@tanstack/react-query"
import {
  AuditTable,
  formatDateTime,
  OptionSelect,
  PageHeader,
  Pagination,
  SearchField,
  TableToolbar,
  useCopy,
} from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"
import { PAGE_SIZE, useDebounced, useListParams } from "@/lib/list-params"

const ALL = "all"
const FILTERS = ["q", "kind"] as const

export function AuditPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const labels = copy.audit
  const { page, values, sort, set, toggleSort, setPage, sortParams } =
    useListParams(FILTERS)
  const q = useDebounced(values.q)
  const params = { q, kind: values.kind, ...sortParams, page, size: PAGE_SIZE }
  const audit = useQuery({
    queryKey: queryKeys.audit(params),
    queryFn: () => gatewayAdmin.audit(params),
    refetchInterval: LIVE,
    placeholderData: (previous) => previous,
  })
  const records = audit.data?.data ?? []
  const info = audit.data?.page
  const table = {
    title: t.audit.title,
    filename: "audit",
    columns: [
      labels.when,
      labels.kind,
      labels.operation,
      labels.principal,
      labels.result,
    ],
    empty: records.length === 0,
    rows: async () =>
      (
        await gatewayAdmin.auditExport({ q, kind: values.kind, ...sortParams })
      ).data.map((record) => [
        formatDateTime(record.recorded_at, copy.locale),
        labels.kinds[record.kind] ?? record.kind,
        record.operation,
        record.lookup_prefix ?? record.principal_id ?? "",
        record.result,
      ]),
  }
  return (
    <>
      <PageHeader
        title={t.audit.title}
        description={t.audit.description}
        actions={<TableExport table={table} />}
      />
      <TableToolbar>
        <SearchField
          value={values.q}
          onChange={(value) => set({ q: value })}
          placeholder={copy.filters.search}
        />
        <OptionSelect
          label={copy.filters.kind}
          value={values.kind || ALL}
          options={[ALL, ...Object.keys(labels.kinds)]}
          labelOf={(value) =>
            value === ALL
              ? `${copy.filters.kind}: ${copy.filters.all}`
              : (labels.kinds[value] ?? value)
          }
          onChange={(value) => set({ kind: value === ALL ? "" : value })}
        />
      </TableToolbar>
      <AuditTable records={records} sort={sort} onSort={toggleSort} />
      {info && (
        <Pagination
          page={info.number}
          totalPages={info.total_pages}
          totalElements={info.total_elements}
          onChange={setPage}
        />
      )}
    </>
  )
}

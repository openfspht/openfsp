import { useQuery } from "@tanstack/react-query"
import { AuditTable, formatDateTime, OptionSelect, PageHeader, Pagination, SearchField, TableToolbar, useCopy } from "@workspace/design-system"
import { useEffect, useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"
import { nextSort, PAGE_SIZE, sortOf, useDebounced, useListParams } from "@/lib/list-params"

const ALL = "all"
const FILTERS = ["q", "kind", "sort", "order"] as const

export function AuditPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const labels = copy.audit
  const { page, values, setFilter, setFilters, setPage } = useListParams(FILTERS)
  const sort = sortOf(values, "recorded_at")
  const [search, setSearch] = useState(values.q)
  const q = useDebounced(search)
  useEffect(() => {
    if (q !== values.q) setFilter("q", q)
  }, [q]) // eslint-disable-line react-hooks/exhaustive-deps
  const params = { q: values.q, kind: values.kind, sort: sort.key, order: sort.ascending ? "asc" : "desc", page, size: PAGE_SIZE }
  const audit = useQuery({ queryKey: queryKeys.audit(params), queryFn: () => gatewayAdmin.audit(params), refetchInterval: LIVE, placeholderData: (previous) => previous })
  const records = audit.data?.data ?? []
  const info = audit.data?.page
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
      <TableToolbar>
        <SearchField value={search} onChange={setSearch} placeholder={copy.filters.search} />
        <OptionSelect
          label={copy.filters.kind}
          value={values.kind || ALL}
          options={[ALL, ...Object.keys(labels.kinds)]}
          labelOf={(value) => (value === ALL ? `${copy.filters.kind}: ${copy.filters.all}` : (labels.kinds[value] ?? value))}
          onChange={(value) => setFilter("kind", value === ALL ? "" : value)}
        />
      </TableToolbar>
      <AuditTable headless records={records} sort={sort} onSort={(key) => setFilters(nextSort(sort, key))} />
      {info && <Pagination page={info.number} totalPages={info.total_pages} totalElements={info.total_elements} onChange={setPage} />}
    </>
  )
}

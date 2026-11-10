import { useQuery } from "@tanstack/react-query"
import {
  AuditTable,
  ExchangeDetail,
  formatDateTime,
  Modal,
  OptionSelect,
  PAGE_SIZE,
  PageHeader,
  Pagination,
  SearchField,
  TableToolbar,
  useCopy,
  useDebounced,
  useListParams,
} from "@workspace/design-system"
import type { AuditRecord } from "@workspace/types"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"

const ALL = "all"
const FILTERS = ["q", "kind"] as const

export function AuditPage() {
  const { t } = useI18n()
  const copy = useCopy()
  const labels = copy.audit
  const [selected, setSelected] = useState<AuditRecord>()
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
      <AuditTable
        records={records}
        sort={sort}
        onSort={toggleSort}
        onSelect={setSelected}
      />
      {info && (
        <Pagination
          page={info.number}
          totalPages={info.total_pages}
          totalElements={info.total_elements}
          onChange={setPage}
        />
      )}
      <Modal
        open={selected?.exchange !== undefined}
        onClose={() => setSelected(undefined)}
        wide
        title={selected?.operation ?? ""}
        description={selected?.request_id}
      >
        {selected?.exchange && (
          <ExchangeDetail
            exchange={{
              status: selected.result,
              receivedAt: selected.recorded_at,
              durationMs: selected.exchange.duration_ms,
              remoteAddress: selected.exchange.remote_address,
              requestHeaders: selected.exchange.request_headers,
              requestBody: selected.exchange.request_body,
              responseHeaders: selected.exchange.response_headers,
              responseBody: selected.exchange.response_body,
            }}
          />
        )}
      </Modal>
    </>
  )
}

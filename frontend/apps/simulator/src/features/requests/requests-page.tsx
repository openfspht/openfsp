import { useQuery } from "@tanstack/react-query"
import {
  ErrorText,
  ExchangeDetail,
  Modal,
  OptionSelect,
  PAGE_SIZE,
  PageHeader,
  Pagination,
  RequestJournalTable,
  SearchField,
  TableToolbar,
  useCopy,
  useDebounced,
  useListParams,
} from "@workspace/design-system"
import type { JournalEntry } from "@workspace/types"
import { useState } from "react"

import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
import { ALL, useOperators } from "@/features/shell/use-operators"
import { useI18n } from "@/i18n/i18n-provider"

const FILTERS = ["q", "operator"] as const

export function RequestsPage() {
  const { t } = useI18n()
  const copy = useCopy().simulator
  const { operators, names, nameOf } = useOperators()
  const [selected, setSelected] = useState<JournalEntry>()
  const { page, values, set, setPage } = useListParams(FILTERS)
  const q = useDebounced(values.q)
  const params = { q, operator: values.operator, page, size: PAGE_SIZE }
  const journal = useQuery({
    queryKey: queryKeys.journal(params),
    queryFn: () => simulatorControl.journal(params),
    refetchInterval: LIVE,
    placeholderData: (previous) => previous,
  })
  const info = journal.data?.page
  return (
    <>
      <PageHeader
        title={t.requests.title}
        description={t.requests.description}
      />
      {journal.isError && (
        <ErrorText className="mb-4">{t.unreachable}</ErrorText>
      )}
      <TableToolbar>
        <SearchField
          value={values.q}
          onChange={(value) => set({ q: value })}
          placeholder={t.requests.search}
        />
        <OptionSelect
          label={copy.operator}
          value={values.operator || ALL}
          options={[ALL, ...operators]}
          labelOf={(value) =>
            value === ALL ? copy.allOperators : nameOf(value)
          }
          onChange={(value) => set({ operator: value === ALL ? "" : value })}
        />
      </TableToolbar>
      <RequestJournalTable
        entries={journal.data?.data ?? []}
        operatorNames={names}
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
        open={selected !== undefined}
        onClose={() => setSelected(undefined)}
        title={selected ? `${selected.method} ${selected.path}` : ""}
        description={selected ? nameOf(selected.operator) : undefined}
      >
        {selected && (
          <ExchangeDetail
            exchange={{
              status: selected.status,
              receivedAt: selected.received_at,
              durationMs: selected.duration_ms,
              remoteAddress: selected.remote_address,
              requestHeaders: selected.headers,
              requestBody: selected.body,
              responseHeaders: selected.response_headers,
              responseBody: selected.response_body,
            }}
          />
        )}
      </Modal>
    </>
  )
}

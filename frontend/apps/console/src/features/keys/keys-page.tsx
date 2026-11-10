import { Button, IssuedKeyNotice, IssueKeyCard, KeysTable, OptionSelect, PageHeader, Pagination, SearchField, TableToolbar, useCopy } from "@workspace/design-system"
import { Plus } from "lucide-react"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"
import { nextSort, paginate, sortOf, sortRows, useListParams } from "@/lib/list-params"

import { useKeys } from "./use-keys"

const FILTERS = ["q", "status", "sort", "order"] as const
const ALL = "all"
const ACTIVE = "active"
const REVOKED = "revoked"

export function KeysPage() {
  const { t } = useI18n()
  const copy = useCopy().keys
  const { keys, issue, revoke } = useKeys()
  const [issuing, setIssuing] = useState(false)
  const filters = useCopy().filters
  const { page, values, setFilter, setFilters, setPage } = useListParams(FILTERS)
  const sort = sortOf(values, "created")
  const needle = values.q.toLowerCase()
  const matching = (keys.data?.data ?? []).filter(
    (key) =>
      (!needle || key.principal.toLowerCase().includes(needle) || key.prefix.toLowerCase().includes(needle)) &&
      (!values.status || (values.status === REVOKED) === Boolean(key.revoked_at))
  )
  const shown = paginate(
    sortRows(matching, sort, {
      principal: (key) => key.principal,
      created: (key) => key.created_at,
      last_used: (key) => key.last_used_at ?? "",
      status: (key) => (key.revoked_at ? 1 : 0),
    }),
    page
  )
  return (
    <div className="space-y-8">
      <PageHeader
        title={t.keys.title}
        description={t.keys.description}
        actions={
          !issuing && (
            <Button
              size="sm"
              onClick={() => {
                issue.reset()
                setIssuing(true)
              }}
            >
              <Plus />
              {copy.issue}
            </Button>
          )
        }
      />
      {issue.data && <IssuedKeyNotice secret={issue.data.secret} onDismiss={() => issue.reset()} />}
      {issuing && (
        <IssueKeyCard
          onIssue={(principal, scopes) => issue.mutate({ principal, scopes }, { onSuccess: () => setIssuing(false) })}
          onCancel={() => setIssuing(false)}
          busy={issue.isPending}
        />
      )}
      <div>
        <TableToolbar>
          <SearchField value={values.q} onChange={(value) => setFilter("q", value)} placeholder={filters.search} />
          <OptionSelect
            label={filters.status}
            value={values.status || ALL}
            options={[ALL, ACTIVE, REVOKED]}
            labelOf={(value) => (value === ALL ? `${filters.status}: ${filters.all}` : value === ACTIVE ? copy.active : copy.revoked)}
            onChange={(value) => setFilter("status", value === ALL ? "" : value)}
          />
        </TableToolbar>
        <KeysTable sort={sort} onSort={(key) => setFilters(nextSort(sort, key))} keys={shown.rows} onRevoke={revoke.mutate} busy={revoke.isPending} />
        <Pagination page={shown.page} totalPages={shown.totalPages} totalElements={shown.totalElements} onChange={setPage} />
      </div>
    </div>
  )
}

import { Button, ConfirmModal, IssuedSecret, IssueKeyForm, KeysTable, Modal, OptionSelect, PageHeader, Pagination, SearchField, TableToolbar, useCopy } from "@workspace/design-system"
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
  const [revoking, setRevoking] = useState<string>()
  const filters = useCopy().filters
  const { page, values, setFilter, setFilters, setPage } = useListParams(FILTERS)
  const sort = sortOf(values, "created")
  const needle = values.q.toLowerCase()
  const status = values.status || ACTIVE
  const matching = (keys.data?.data ?? []).filter(
    (key) =>
      (!needle || key.principal.toLowerCase().includes(needle) || key.prefix.toLowerCase().includes(needle)) &&
      (status === ALL || (status === REVOKED) === Boolean(key.revoked_at))
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
  const revokingKey = (keys.data?.data ?? []).find((key) => key.id === revoking)
  return (
    <div className="space-y-8">
      <PageHeader
        title={t.keys.title}
        description={t.keys.description}
        actions={
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
        }
      />
      <div>
        <TableToolbar>
          <SearchField value={values.q} onChange={(value) => setFilter("q", value)} placeholder={filters.search} />
          <OptionSelect
            label={filters.status}
            value={status}
            options={[ACTIVE, REVOKED, ALL]}
            labelOf={(value) => `${filters.status}: ${value === ALL ? filters.all : value === ACTIVE ? copy.active : copy.revoked}`}
            onChange={(value) => setFilter("status", value === ACTIVE ? "" : value)}
          />
        </TableToolbar>
        <KeysTable sort={sort} onSort={(key) => setFilters(nextSort(sort, key))} keys={shown.rows} onRevoke={setRevoking} busy={revoke.isPending} />
        <Pagination page={shown.page} totalPages={shown.totalPages} totalElements={shown.totalElements} onChange={setPage} />
      </div>
      <Modal open={issuing} onClose={() => setIssuing(false)} title={copy.issue} description={copy.description}>
        <IssueKeyForm
          onIssue={(principal, scopes) => issue.mutate({ principal, scopes }, { onSuccess: () => setIssuing(false) })}
          onCancel={() => setIssuing(false)}
          busy={issue.isPending}
        />
      </Modal>
      <Modal open={Boolean(issue.data)} onClose={() => issue.reset()} title={copy.issuedTitle} description={copy.copyNow} closable={false}>
        {issue.data && <IssuedSecret secret={issue.data.secret} onDone={() => issue.reset()} />}
      </Modal>
      <ConfirmModal
        open={revoking !== undefined}
        onClose={() => setRevoking(undefined)}
        onConfirm={() => revoking && revoke.mutate(revoking, { onSuccess: () => setRevoking(undefined) })}
        title={copy.revokeTitle}
        description={copy.revokeDescription(revokingKey?.principal ?? "")}
        confirmLabel={copy.confirmRevoke}
        destructive
        busy={revoke.isPending}
      />
    </div>
  )
}

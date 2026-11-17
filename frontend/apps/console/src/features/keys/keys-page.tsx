import {
    Button,
    ConfirmModal,
    Modal,
    OptionSelect,
    PAGE_SIZE,
    PageHeader,
    Pagination,
    SearchField,
    type SortState,
    TableToolbar,
    useCopy,
    useDebounced,
    useListParams,
} from "@workspace/design-system"
import { Plus } from "lucide-react"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

import { IssuedSecret, IssueKeyForm, KeysTable } from "./keys-card"
import { useKeys } from "./use-keys"

const FILTERS = ["q", "status"] as const
const ALL = "all"
const ACTIVE = "active"
const REVOKED = "revoked"
const EXPIRED = "expired"
const NEWEST_KEYS: SortState = { key: "created", ascending: false }

export function KeysPage() {
    const { t } = useI18n()
    const copy = t.keys
    const [issuing, setIssuing] = useState(false)
    const [revoking, setRevoking] = useState<string>()
    const filters = useCopy().filters
    const { page, values, sort, set, toggleSort, setPage, sortParams } = useListParams(
        FILTERS,
        NEWEST_KEYS
    )
    const status = values.status || ACTIVE
    const q = useDebounced(values.q)
    const { keys, issue, revoke } = useKeys({
        q,
        status,
        ...sortParams,
        page,
        size: PAGE_SIZE,
    })
    const all = keys.data?.data ?? []
    const info = keys.data?.page
    const revokingKey = all.find((key) => key.id === revoking)
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
                    <SearchField
                        value={values.q}
                        onChange={(value) => set({ q: value })}
                        placeholder={filters.search}
                    />
                    <OptionSelect
                        label={filters.status}
                        value={status}
                        options={[ACTIVE, EXPIRED, REVOKED, ALL]}
                        labelOf={(value) =>
                            `${filters.status}: ${value === ALL ? filters.all : copy[value as typeof ACTIVE | typeof EXPIRED | typeof REVOKED]}`
                        }
                        onChange={(value) => set({ status: value === ACTIVE ? "" : value })}
                    />
                </TableToolbar>
                <KeysTable
                    sort={sort}
                    onSort={toggleSort}
                    keys={all}
                    onRevoke={(id) => {
                        revoke.reset()
                        setRevoking(id)
                    }}
                    busy={revoke.isPending}
                />
                {info && (
                    <Pagination
                        page={info.number}
                        totalPages={info.total_pages}
                        totalElements={info.total_elements}
                        onChange={setPage}
                    />
                )}
            </div>
            <Modal
                open={issuing}
                onClose={() => setIssuing(false)}
                title={copy.issue}
                description={copy.issueDescription}
            >
                <IssueKeyForm
                    onIssue={(principal, scopes) =>
                        issue.mutate({ principal, scopes }, { onSuccess: () => setIssuing(false) })
                    }
                    onCancel={() => setIssuing(false)}
                    busy={issue.isPending}
                    error={issue.error}
                />
            </Modal>
            <Modal
                open={Boolean(issue.data)}
                onClose={() => issue.reset()}
                title={copy.issuedTitle}
                description={copy.copyNow}
                closable={false}
            >
                {issue.data && (
                    <IssuedSecret secret={issue.data.secret} onDone={() => issue.reset()} />
                )}
            </Modal>
            <ConfirmModal
                open={revoking !== undefined}
                onClose={() => setRevoking(undefined)}
                onConfirm={() =>
                    revoking && revoke.mutate(revoking, { onSuccess: () => setRevoking(undefined) })
                }
                title={copy.revokeTitle}
                description={copy.revokeDescription(revokingKey?.principal ?? "")}
                confirmLabel={copy.confirmRevoke}
                destructive
                busy={revoke.isPending}
                error={revoke.error}
            />
        </div>
    )
}

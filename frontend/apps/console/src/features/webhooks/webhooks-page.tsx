import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
    ActionError,
    Button,
    ConfirmModal,
    Modal,
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

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { AccountFilter } from "@/features/accounts/account-select"
import { useI18n } from "@/i18n/i18n-provider"

import { AddWebhookForm, SigningKeyCard, WebhookEndpointsCard } from "./webhook-endpoints-card"

const FILTERS = ["q", "account"] as const
const BY_NAME: SortState = { key: "name", ascending: true }

export function WebhooksPage() {
    const { t } = useI18n()
    const copy = t.webhooks
    const queryClient = useQueryClient()
    const [adding, setAdding] = useState(false)
    const [removing, setRemoving] = useState<string>()
    const [rotating, setRotating] = useState(false)
    const filters = useCopy().filters
    const { page, values, sort, set, toggleSort, setPage, sortParams } = useListParams(
        FILTERS,
        BY_NAME
    )
    const q = useDebounced(values.q)
    const params = { account: values.account, q, ...sortParams, page, size: PAGE_SIZE }
    const refresh = () =>
        Promise.all(
            [queryKeys.allWebhookEndpoints, queryKeys.overview].map((queryKey) =>
                queryClient.invalidateQueries({ queryKey })
            )
        )
    const endpoints = useQuery({
        queryKey: queryKeys.webhookEndpoints(params),
        queryFn: () => gatewayAdmin.webhookEndpoints(params),
        refetchInterval: LIVE,
        placeholderData: (previous) => previous,
    })
    const info = endpoints.data?.page
    const enable = useMutation({
        mutationFn: gatewayAdmin.enableEndpoint,
        onSuccess: refresh,
    })
    const add = useMutation({
        mutationFn: gatewayAdmin.addEndpoint,
        onSuccess: () => {
            setAdding(false)
            return refresh()
        },
    })
    const removal = useMutation({
        mutationFn: gatewayAdmin.removeEndpoint,
        onSuccess: refresh,
    })
    const configuration = useQuery({
        queryKey: queryKeys.configuration,
        queryFn: gatewayAdmin.configuration,
    })
    const rotate = useMutation({
        mutationFn: gatewayAdmin.rotateSigningKey,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.configuration }),
    })
    const open = () => {
        add.reset()
        setAdding(true)
    }
    return (
        <div className="space-y-8">
            <PageHeader
                title={t.webhooks.title}
                description={t.webhooks.description}
                actions={
                    <Button size="sm" onClick={open}>
                        <Plus />
                        {copy.addTitle}
                    </Button>
                }
            />
            <div>
                <TableToolbar>
                    <AccountFilter
                        value={values.account}
                        onChange={(account) => set({ account })}
                    />
                    <SearchField
                        value={values.q}
                        onChange={(value) => set({ q: value })}
                        placeholder={filters.search}
                    />
                </TableToolbar>
                <ActionError error={enable.error} className="mb-4" />
                <WebhookEndpointsCard
                    sort={sort}
                    onSort={toggleSort}
                    endpoints={endpoints.data?.data ?? []}
                    onEnable={enable.mutate}
                    onRemove={(id) => {
                        removal.reset()
                        setRemoving(id)
                    }}
                    busy={enable.isPending || removal.isPending}
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
            <SigningKeyCard
                keys={configuration.data?.signing}
                onRotate={() => {
                    rotate.reset()
                    setRotating(true)
                }}
                busy={rotate.isPending}
            />
            <Modal open={adding} onClose={() => setAdding(false)} title={copy.addTitle}>
                <AddWebhookForm
                    account={values.account}
                    onAdd={add.mutate}
                    onCancel={() => setAdding(false)}
                    error={add.error}
                    busy={add.isPending}
                />
            </Modal>
            <ConfirmModal
                open={removing !== undefined}
                onClose={() => setRemoving(undefined)}
                onConfirm={() =>
                    removing &&
                    removal.mutate(removing, { onSuccess: () => setRemoving(undefined) })
                }
                title={copy.removeTitle}
                description={copy.removeDescription(removing ?? "")}
                confirmLabel={copy.confirmRemove}
                destructive
                busy={removal.isPending}
                error={removal.error}
            />
            <ConfirmModal
                open={rotating}
                onClose={() => setRotating(false)}
                onConfirm={() => rotate.mutate(undefined, { onSuccess: () => setRotating(false) })}
                title={copy.rotateTitleConfirm}
                description={copy.rotateConfirmDescription}
                confirmLabel={copy.rotate}
                busy={rotate.isPending}
                error={rotate.error}
            />
        </div>
    )
}

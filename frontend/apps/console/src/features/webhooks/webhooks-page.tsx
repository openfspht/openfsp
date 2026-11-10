import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AddWebhookCard, Button, PageHeader, Pagination, SearchField, SigningKeyCard, TableToolbar, useCopy, WebhookEndpointsCard } from "@workspace/design-system"
import { Plus } from "lucide-react"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"
import { nextSort, paginate, sortOf, sortRows, useListParams } from "@/lib/list-params"

const FILTERS = ["q", "sort", "order"] as const

export function WebhooksPage() {
  const { t } = useI18n()
  const copy = useCopy().webhooks
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const filters = useCopy().filters
  const { page, values, setFilter, setFilters, setPage } = useListParams(FILTERS)
  const sort = sortOf(values, "name", true)
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.webhookEndpoints })
  const endpoints = useQuery({ queryKey: queryKeys.webhookEndpoints, queryFn: gatewayAdmin.webhookEndpoints, refetchInterval: LIVE })
  const needle = values.q.toLowerCase()
  const shown = paginate(
    sortRows(
      (endpoints.data?.data ?? []).filter((endpoint) => !needle || endpoint.name.toLowerCase().includes(needle) || endpoint.url.toLowerCase().includes(needle)),
      sort,
      { name: (endpoint) => endpoint.name, url: (endpoint) => endpoint.url, status: (endpoint) => (endpoint.disabled_at ? 2 : endpoint.enabled ? 0 : 1) }
    ),
    page
  )
  const enable = useMutation({ mutationFn: gatewayAdmin.enableEndpoint, onSuccess: refresh })
  const add = useMutation({
    mutationFn: gatewayAdmin.addEndpoint,
    onSuccess: () => {
      setAdding(false)
      return refresh()
    },
  })
  const removal = useMutation({ mutationFn: gatewayAdmin.removeEndpoint, onSuccess: refresh })
  const configuration = useQuery({ queryKey: queryKeys.configuration, queryFn: gatewayAdmin.configuration })
  const rotate = useMutation({ mutationFn: gatewayAdmin.rotateSigningKey, onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.configuration }) })
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
          !adding && (
            <Button size="sm" onClick={open}>
              <Plus />
              {copy.addTitle}
            </Button>
          )
        }
      />
      {adding && <AddWebhookCard onAdd={add.mutate} onCancel={() => setAdding(false)} error={add.error?.message} busy={add.isPending} />}
      <div>
        <TableToolbar>
          <SearchField value={values.q} onChange={(value) => setFilter("q", value)} placeholder={filters.search} />
        </TableToolbar>
        <WebhookEndpointsCard sort={sort} onSort={(key) => setFilters(nextSort(sort, key))} endpoints={shown.rows} onEnable={enable.mutate} onRemove={removal.mutate} busy={enable.isPending || removal.isPending} />
        <Pagination page={shown.page} totalPages={shown.totalPages} totalElements={shown.totalElements} onChange={setPage} />
      </div>
      <SigningKeyCard keys={configuration.data?.signing} onRotate={() => rotate.mutate()} busy={rotate.isPending} />
    </div>
  )
}

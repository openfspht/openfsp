import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AddWebhookCard, Button, PageHeader, SigningKeyCard, useCopy, WebhookEndpointsCard } from "@workspace/design-system"
import { Plus } from "lucide-react"
import { useState } from "react"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

export function WebhooksPage() {
  const { t } = useI18n()
  const copy = useCopy().webhooks
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.webhookEndpoints })
  const endpoints = useQuery({ queryKey: queryKeys.webhookEndpoints, queryFn: gatewayAdmin.webhookEndpoints, refetchInterval: LIVE })
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
      <WebhookEndpointsCard endpoints={endpoints.data?.data ?? []} onEnable={enable.mutate} onRemove={removal.mutate} busy={enable.isPending || removal.isPending} />
      <SigningKeyCard keys={configuration.data?.signing} onRotate={() => rotate.mutate()} busy={rotate.isPending} />
    </div>
  )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { PageHeader, SigningKeyCard, SplitView, WebhookEndpointsCard } from "@workspace/design-system"

import { gatewayAdmin } from "@/api/gateway-admin"
import { LIVE, queryKeys } from "@/api/query-keys"
import { useI18n } from "@/i18n/i18n-provider"

export function WebhooksPage() {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const endpoints = useQuery({ queryKey: queryKeys.webhookEndpoints, queryFn: gatewayAdmin.webhookEndpoints, refetchInterval: LIVE })
  const enable = useMutation({
    mutationFn: gatewayAdmin.enableEndpoint,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.webhookEndpoints }),
  })
  const rotate = useMutation({ mutationFn: gatewayAdmin.rotateSigningKey })
  return (
    <>
      <PageHeader title={t.webhooks.title} description={t.webhooks.description} />
      <SplitView
        main={<WebhookEndpointsCard endpoints={endpoints.data?.data ?? []} onEnable={enable.mutate} busy={enable.isPending} />}
        aside={<SigningKeyCard onRotate={() => rotate.mutate()} rotatedKid={rotate.data?.kid} busy={rotate.isPending} />}
      />
    </>
  )
}

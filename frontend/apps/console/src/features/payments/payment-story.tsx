import { Button, PaymentDetail, PaymentWebhooksCard } from "@workspace/design-system"
import { Link } from "react-router"

import { useProviderNames } from "@/features/providers/use-provider-names"
import { useI18n } from "@/i18n/i18n-provider"

import { usePayment } from "./use-payment"

export function PaymentStory({ id, linked = false, split = false }: { id: string; linked?: boolean; split?: boolean }) {
  const { t } = useI18n()
  const names = useProviderNames()
  const { detail, attest } = usePayment(id)
  if (!detail) return null
  const story = (
    <PaymentDetail
      withWebhooks={!split}
      detail={detail}
      onAttest={attest.mutate}
      attesting={attest.isPending}
      providerName={names[detail.payment.provider]}
      actions={
        linked && (
          <Button size="xs" variant="outline" nativeButton={false} render={<Link to={`/payments/${id}`} />}>
            {t.activity.openPayment}
          </Button>
        )
      }
    />
  )
  if (!split) return story
  return (
    <div className="grid items-start gap-8 lg:grid-cols-2">
      {story}
      <PaymentWebhooksCard events={detail.events} />
    </div>
  )
}

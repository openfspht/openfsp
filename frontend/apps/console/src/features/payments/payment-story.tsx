import { Button, ConfirmModal, PaymentDetail, PaymentWebhooksCard, useCopy } from "@workspace/design-system"
import type { AttestedStatus } from "@workspace/types"
import { useState } from "react"
import { Link } from "react-router"

import { useProviderNames } from "@/features/providers/use-provider-names"
import { useI18n } from "@/i18n/i18n-provider"

import { usePayment } from "./use-payment"

export function PaymentStory({ id, linked = false, split = false }: { id: string; linked?: boolean; split?: boolean }) {
  const { t } = useI18n()
  const names = useProviderNames()
  const { detail, attest } = usePayment(id)
  const copy = useCopy()
  const [attesting, setAttesting] = useState<AttestedStatus>()
  if (!detail) return null
  const confirm = (
    <ConfirmModal
      open={attesting !== undefined}
      onClose={() => setAttesting(undefined)}
      onConfirm={() => attesting && attest.mutate(attesting, { onSuccess: () => setAttesting(undefined) })}
      title={copy.attestation.confirmTitle(attesting ? copy.status[attesting] : "")}
      description={copy.attestation.confirmDescription}
      confirmLabel={attesting && attesting !== "canceled" ? copy.attestation[attesting] : ""}
      destructive={attesting !== "succeeded"}
      busy={attest.isPending}
    />
  )
  const story = (
    <PaymentDetail
      withWebhooks={!split}
      detail={detail}
      onAttest={setAttesting}
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
  if (!split)
    return (
      <>
        {story}
        {confirm}
      </>
    )
  return (
    <div className="grid items-start gap-8 lg:grid-cols-2">
      {story}
      <PaymentWebhooksCard events={detail.events} />
      {confirm}
    </div>
  )
}

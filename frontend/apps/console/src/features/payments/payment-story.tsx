import { Button, EmptyText, Modal, useCopy } from "@workspace/design-system"
import { useState } from "react"
import { Link } from "react-router"

import { useProviders } from "@/features/providers/use-providers"
import { useI18n } from "@/i18n/i18n-provider"

import { type AttestableStatus, AttestationForm } from "./attestation-actions"
import { PaymentDetail, PaymentWebhooksCard } from "./payment-detail"
import { usePayment } from "./use-payment"

export function PaymentStory({
  id,
  linked = false,
  split = false,
}: {
  id: string
  linked?: boolean
  split?: boolean
}) {
  const { t } = useI18n()
  const { names } = useProviders()
  const { detail, missing, attest } = usePayment(id)
  const copy = useCopy()
  const [attesting, setAttesting] = useState<AttestableStatus>()
  if (missing) return <EmptyText>{t.payment.notFound}</EmptyText>
  if (!detail) return null
  return (
    <div
      className={split ? "grid items-start gap-8 lg:grid-cols-2" : "space-y-6"}
    >
      <PaymentDetail
        detail={detail}
        onAttest={(status) => {
          attest.reset()
          setAttesting(status)
        }}
        attesting={attest.isPending}
        providerName={names[detail.payment.provider]}
        actions={
          linked && (
            <Button
              size="xs"
              variant="outline"
              nativeButton={false}
              render={<Link to={`/payments/${id}`} />}
            >
              {t.activity.openPayment}
            </Button>
          )
        }
      />
      <PaymentWebhooksCard events={detail.events} />
      <Modal
        open={attesting !== undefined}
        onClose={() => setAttesting(undefined)}
        title={t.attestation.confirmTitle(
          attesting ? copy.status[attesting] : ""
        )}
        description={t.attestation.confirmDescription}
      >
        {attesting && (
          <AttestationForm
            status={attesting}
            onAttest={(attestation) =>
              attest.mutate(attestation, {
                onSuccess: () => setAttesting(undefined),
              })
            }
            onCancel={() => setAttesting(undefined)}
            busy={attest.isPending}
            error={attest.error}
          />
        )}
      </Modal>
    </div>
  )
}

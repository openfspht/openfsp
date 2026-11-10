import { Button, EmptyText, PageHeader } from "@workspace/design-system"
import { Link, useParams } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

import { PaymentStory } from "./payment-story"
import { usePayment } from "./use-payment"

export function PaymentPage() {
  const { t } = useI18n()
  const { id = "" } = useParams()
  const { missing } = usePayment(id)
  return (
    <div className="max-w-3xl">
      <PageHeader
        title={t.payments.title}
        actions={
          <Button size="sm" variant="outline" nativeButton={false} render={<Link to="/payments" />}>
            {t.payment.back}
          </Button>
        }
      />
      {missing ? <EmptyText>{t.payment.notFound}</EmptyText> : <PaymentStory id={id} />}
    </div>
  )
}

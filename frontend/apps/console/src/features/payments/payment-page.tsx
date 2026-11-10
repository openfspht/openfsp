import { Button, PageHeader } from "@workspace/design-system"
import { Link, useParams } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

import { PaymentStory } from "./payment-story"

export function PaymentPage() {
  const { t } = useI18n()
  const { id = "" } = useParams()
  return (
    <div>
      <PageHeader
        title={t.payments.title}
        actions={
          <Button
            size="sm"
            variant="outline"
            nativeButton={false}
            render={<Link to="/payments" />}
          >
            {t.payment.back}
          </Button>
        }
      />
      <PaymentStory id={id} split />
    </div>
  )
}

import { ErrorText, PageHeader, PaymentsTable } from "@workspace/design-system"
import { useNavigate } from "react-router"

import { useProviderNames } from "@/features/providers/use-provider-names"
import { useI18n } from "@/i18n/i18n-provider"

import { usePayments } from "./use-payment"

export function PaymentsPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const { list, unreachable } = usePayments()
  const names = useProviderNames()
  return (
    <>
      <PageHeader title={t.payments.title} description={t.payments.description} />
      {unreachable && <ErrorText className="mb-4">{t.activity.unreachable}</ErrorText>}
      <PaymentsTable headless payments={list} providerNames={names} onSelect={(id) => navigate(`/payments/${id}`)} />
    </>
  )
}

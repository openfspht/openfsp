import { ErrorText, formatDateTime, formatMoney, PageHeader, PaymentsTable, useCopy } from "@workspace/design-system"
import { useNavigate } from "react-router"

import { useProviderLogos, useProviderNames } from "@/features/providers/use-provider-names"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"

import { usePayments } from "./use-payment"

export function PaymentsPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const { list, unreachable } = usePayments()
  const names = useProviderNames()
  const logos = useProviderLogos()
  const copy = useCopy()
  const labels = copy.paymentsTable
  const table = {
    title: t.payments.title,
    filename: "payments",
    columns: [labels.reference, labels.provider, labels.amount, labels.status, labels.created],
    rows: list.map((payment) => [
      payment.reference,
      names[payment.provider] ?? payment.provider,
      formatMoney(payment.amount, copy.locale),
      copy.status[payment.status],
      formatDateTime(payment.created_at, copy.locale),
    ]),
  }
  return (
    <>
      <PageHeader title={t.payments.title} description={t.payments.description} actions={<TableExport table={table} />} />
      {unreachable && <ErrorText className="mb-4">{t.activity.unreachable}</ErrorText>}
      <PaymentsTable headless payments={list} providerNames={names} providerLogos={logos} onSelect={(id) => navigate(`/payments/${id}`)} />
    </>
  )
}

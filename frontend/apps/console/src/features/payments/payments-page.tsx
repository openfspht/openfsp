import { useQuery } from "@tanstack/react-query"
import {
  ErrorText,
  formatDateTime,
  formatMoney,
  OptionSelect,
  PageHeader,
  Pagination,
  PaymentsTable,
  SearchField,
  TableToolbar,
  useCopy,
} from "@workspace/design-system"
import type { PaymentStatus } from "@workspace/types"
import { useEffect, useState } from "react"
import { useNavigate } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useProviderLogos, useProviderNames } from "@/features/providers/use-provider-names"
import { TableExport } from "@/features/shell/table-export"
import { useI18n } from "@/i18n/i18n-provider"
import { PAGE_SIZE, useDebounced, useListParams } from "@/lib/list-params"

import { usePayments } from "./use-payment"

const ALL = "all"
const STATUSES: readonly PaymentStatus[] = ["pending", "succeeded", "failed", "expired", "canceled"]
const FILTERS = ["q", "status", "provider"] as const

export function PaymentsPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const copy = useCopy()
  const names = useProviderNames()
  const logos = useProviderLogos()
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: gatewayAdmin.providers })
  const { page, values, setFilter, setPage } = useListParams(FILTERS)
  const [search, setSearch] = useState(values.q)
  const q = useDebounced(search)
  useEffect(() => {
    if (q !== values.q) setFilter("q", q)
  }, [q]) // eslint-disable-line react-hooks/exhaustive-deps
  const { list, page: info, unreachable } = usePayments({ q: values.q, status: values.status, provider: values.provider, page, size: PAGE_SIZE })
  const labels = copy.paymentsTable
  const providerIds = providers.data?.data.map((provider) => provider.id) ?? []
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
      <TableToolbar>
        <SearchField value={search} onChange={setSearch} placeholder={t.activity.search} />
        <OptionSelect
          label={copy.filters.status}
          value={values.status || ALL}
          options={[ALL, ...STATUSES]}
          labelOf={(value) => (value === ALL ? `${copy.filters.status}: ${copy.filters.all}` : copy.status[value as PaymentStatus])}
          onChange={(value) => setFilter("status", value === ALL ? "" : value)}
        />
        <OptionSelect
          label={copy.filters.provider}
          value={values.provider || ALL}
          options={[ALL, ...providerIds]}
          labelOf={(value) => (value === ALL ? `${copy.filters.provider}: ${copy.filters.all}` : (names[value] ?? value))}
          onChange={(value) => setFilter("provider", value === ALL ? "" : value)}
        />
      </TableToolbar>
      <PaymentsTable headless payments={list} providerNames={names} providerLogos={logos} onSelect={(id) => navigate(`/payments/${id}`)} />
      {info && <Pagination page={info.number} totalPages={info.total_pages} totalElements={info.total_elements} onChange={setPage} />}
    </>
  )
}

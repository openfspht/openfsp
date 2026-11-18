import {
    ErrorText,
    formatDateTime,
    formatMoney,
    OptionSelect,
    PAGE_SIZE,
    PageHeader,
    Pagination,
    SearchField,
    TableToolbar,
    useCopy,
    useDebounced,
    useListParams,
} from "@workspace/design-system"
import { PAYMENT_STATUSES, type PaymentStatus } from "@workspace/types"
import { useNavigate } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { useProviders } from "@/features/providers/use-providers"
import { TableExport } from "@/features/shell/table-export"
import { AccountFilter } from "@/features/accounts/account-select"
import { useI18n } from "@/i18n/i18n-provider"

import { PaymentsTable } from "./payments-table"
import { usePayments } from "./use-payment"

const ALL = "all"
const FILTERS = ["q", "status", "provider", "account"] as const

export function PaymentsPage() {
    const { t } = useI18n()
    const navigate = useNavigate()
    const copy = useCopy()
    const { ids, names, logos } = useProviders()
    const { page, values, sort, set, toggleSort, setPage, sortParams } = useListParams(FILTERS)
    const q = useDebounced(values.q)
    const {
        list,
        page: info,
        unreachable,
    } = usePayments({
        account: values.account,
        q,
        status: values.status,
        provider: values.provider,
        ...sortParams,
        page,
        size: PAGE_SIZE,
    })
    const labels = t.paymentsTable
    const table = {
        title: t.payments.title,
        filename: "payments",
        columns: [labels.reference, labels.provider, labels.amount, labels.status, labels.created],
        rows: async () =>
            (
                await gatewayAdmin.paymentsExport({
                    account: values.account,
                    q,
                    status: values.status,
                    provider: values.provider,
                    ...sortParams,
                })
            ).data.map((payment) => [
                payment.reference,
                names[payment.provider] ?? payment.provider,
                formatMoney(payment.amount, copy.locale),
                copy.status[payment.status],
                formatDateTime(payment.created_at, copy.locale),
            ]),
        empty: list.length === 0,
    }
    return (
        <>
            <PageHeader
                title={t.payments.title}
                description={t.payments.description}
                actions={<TableExport table={table} />}
            />
            {unreachable && <ErrorText className="mb-4">{t.activity.unreachable}</ErrorText>}
            <TableToolbar>
                <AccountFilter value={values.account} onChange={(account) => set({ account })} />
                <SearchField
                    value={values.q}
                    onChange={(value) => set({ q: value })}
                    placeholder={t.activity.search}
                />
                <OptionSelect
                    label={copy.filters.status}
                    value={values.status || ALL}
                    options={[ALL, ...PAYMENT_STATUSES]}
                    labelOf={(value) =>
                        value === ALL
                            ? `${copy.filters.status}: ${copy.filters.all}`
                            : copy.status[value as PaymentStatus]
                    }
                    onChange={(value) => set({ status: value === ALL ? "" : value })}
                />
                <OptionSelect
                    label={copy.filters.provider}
                    value={values.provider || ALL}
                    options={[ALL, ...ids]}
                    labelOf={(value) =>
                        value === ALL
                            ? `${copy.filters.provider}: ${copy.filters.all}`
                            : (names[value] ?? value)
                    }
                    onChange={(value) => set({ provider: value === ALL ? "" : value })}
                />
            </TableToolbar>
            <PaymentsTable
                sort={sort}
                onSort={toggleSort}
                payments={list}
                providerNames={names}
                providerLogos={logos}
                onSelect={(id) => navigate(`/payments/${id}`)}
            />
            {info && (
                <Pagination
                    page={info.number}
                    totalPages={info.total_pages}
                    totalElements={info.total_elements}
                    onChange={setPage}
                />
            )}
        </>
    )
}

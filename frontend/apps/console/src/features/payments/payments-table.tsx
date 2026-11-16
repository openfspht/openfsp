import {
  Card,
  CardContent,
  Money,
  ProviderLogo,
  SortableHead,
  type SortState,
  StatusIndicator,
  TableEmptyRow,
  Tabular,
  Time,
} from "@workspace/design-system"
import type { Payment } from "@workspace/types"
import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
} from "@workspace/ui/components/table"
import { Link } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

interface PaymentsTableProps {
  payments: Payment[]
  onSelect: (id: string) => void
  providerNames?: Record<string, string>
  providerLogos?: Record<string, string | null>
  sort?: SortState
  onSort?: (key: string) => void
}

export function PaymentsTable({
  payments,
  onSelect,
  providerNames = {},
  providerLogos = {},
  sort,
  onSort,
}: PaymentsTableProps) {
  const withLogos = Object.values(providerLogos).some(Boolean)
  const copy = useI18n().t.paymentsTable
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <SortableHead
                label={copy.reference}
                sortKey="reference"
                sort={sort}
                onSort={onSort}
              />
              <SortableHead
                label={copy.provider}
                sortKey="provider"
                sort={sort}
                onSort={onSort}
              />
              <SortableHead
                label={copy.amount}
                sortKey="amount"
                sort={sort}
                onSort={onSort}
                align="right"
              />
              <SortableHead
                label={copy.status}
                sortKey="status"
                sort={sort}
                onSort={onSort}
              />
              <SortableHead
                label={copy.created}
                sortKey="created_at"
                sort={sort}
                onSort={onSort}
                align="right"
              />
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {payments.length === 0 && (
              <TableEmptyRow columns={5}>{copy.empty}</TableEmptyRow>
            )}
            {payments.map((payment) => (
              <TableRow
                key={payment.id}
                onClick={(event) => {
                  if (!isFromLink(event)) onSelect(payment.id)
                }}
                className="cursor-pointer"
              >
                <TableCell>
                  <Link
                    to={`/payments/${payment.id}`}
                    className="outline-none hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                  >
                    <Tabular>{payment.reference}</Tabular>
                  </Link>
                </TableCell>
                <TableCell>
                  <span className="flex items-center gap-3">
                    {withLogos && (
                      <ProviderLogo
                        src={providerLogos[payment.provider]}
                        size="sm"
                      />
                    )}
                    {providerNames[payment.provider] ?? payment.provider}
                  </span>
                </TableCell>
                <TableCell className="text-right">
                  <Money value={payment.amount} />
                </TableCell>
                <TableCell>
                  <StatusIndicator status={payment.status} />
                </TableCell>
                <TableCell className="text-right text-muted-foreground">
                  <Time value={payment.created_at} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

const isFromLink = (event: React.MouseEvent) =>
  event.target instanceof Element && event.target.closest("a") !== null

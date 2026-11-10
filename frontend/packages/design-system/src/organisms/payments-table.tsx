import type { Payment } from "@workspace/types"
import { Table, TableBody, TableCell, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Card, CardContent, ProviderLogo, Tabular, Money, StatusIndicator, Time } from "../atoms"
import { useCopy } from "../copy"
import { SortableHead, type SortState, TableEmptyRow } from "../molecules"

interface PaymentsTableProps {
  payments: Payment[]
  onSelect: (id: string) => void
  providerNames?: Record<string, string>
  providerLogos?: Record<string, string | null>
  sort?: SortState
  onSort?: (key: string) => void
}

export function PaymentsTable({ payments, onSelect, providerNames = {}, providerLogos = {}, sort, onSort }: PaymentsTableProps) {
  const withLogos = Object.values(providerLogos).some(Boolean)
  const copy = useCopy().paymentsTable
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <SortableHead label={copy.reference} sortKey="reference" sort={sort} onSort={onSort} />
              <SortableHead label={copy.provider} sortKey="provider" sort={sort} onSort={onSort} />
              <SortableHead label={copy.amount} sortKey="amount" sort={sort} onSort={onSort} align="right" />
              <SortableHead label={copy.status} sortKey="status" sort={sort} onSort={onSort} />
              <SortableHead label={copy.created} sortKey="created_at" sort={sort} onSort={onSort} align="right" />
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {payments.length === 0 && (
              <TableEmptyRow columns={5}>{copy.empty}</TableEmptyRow>
            )}
            {payments.map((payment) => (
              <TableRow
                key={payment.id}
                onClick={() => onSelect(payment.id)}
                className="cursor-pointer"
              >
                <TableCell>
                  <Tabular>{payment.reference}</Tabular>
                </TableCell>
                <TableCell>
                  <span className="flex items-center gap-3">
                    {withLogos && <ProviderLogo src={providerLogos[payment.provider]} size="sm" />}
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

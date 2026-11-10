import type { Payment } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"
import { cn } from "@workspace/ui/lib/utils"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, Mono, Money, StatusIndicator, Time } from "../atoms"
import { useCopy } from "../copy"

interface PaymentsTableProps {
  payments: Payment[]
  selectedId?: string
  onSelect: (id: string) => void
}

export function PaymentsTable({ payments, selectedId, onSelect }: PaymentsTableProps) {
  const copy = useCopy().paymentsTable
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.reference}</TableHead>
              <TableHead>{copy.provider}</TableHead>
              <TableHead className="text-right">{copy.amount}</TableHead>
              <TableHead>{copy.status}</TableHead>
              <TableHead className="text-right">{copy.created}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {payments.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="py-10 text-center text-muted-foreground">
                  {copy.empty}
                </TableCell>
              </TableRow>
            )}
            {payments.map((payment) => (
              <TableRow
                key={payment.id}
                onClick={() => onSelect(payment.id)}
                className={cn("cursor-pointer", payment.id === selectedId && "bg-muted")}
              >
                <TableCell>
                  <Mono>{payment.reference}</Mono>
                </TableCell>
                <TableCell>{payment.provider}</TableCell>
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

import type { SimulatedPayment } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Tabular, Muted, Time } from "../atoms"

interface SimulatedPaymentsTableProps {
  payments: SimulatedPayment[]
  onDeliverCallback: (paymentId: string) => void
  busy?: boolean
}

export function SimulatedPaymentsTable({ payments, onDeliverCallback, busy }: SimulatedPaymentsTableProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Simulated payments</CardTitle>
        <CardDescription>What the simulated operators hold, as the gateway created them.</CardDescription>
      </CardHeader>
      <CardContent>
        {payments.length === 0 ? (
          <EmptyText>No payment reached the simulator yet.</EmptyText>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Payment</TableHead>
                <TableHead>Scenario</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead className="text-right">Callbacks</TableHead>
                <TableHead className="text-right">Lookups</TableHead>
                <TableHead className="text-right">Created</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody striped>
              {payments.map((payment) => (
                <TableRow key={payment.payment_id}>
                  <TableCell>
                    <Tabular className="block">{payment.payment_id}</Tabular>
                    <Muted className="text-xs">{payment.operator}</Muted>
                  </TableCell>
                  <TableCell>
                    <Tabular>{payment.scenario}</Tabular>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {payment.amount} HTG
                    {payment.reported_amount && payment.reported_amount !== payment.amount && <Muted className="block text-xs">reported {payment.reported_amount}</Muted>}
                  </TableCell>
                  <TableCell>{payment.outcome ? <Badge variant="outline">{payment.outcome.toLowerCase()}</Badge> : <Muted className="text-xs">waiting</Muted>}</TableCell>
                  <TableCell className="text-right tabular-nums">{payment.callbacks_sent}</TableCell>
                  <TableCell className="text-right tabular-nums">{payment.lookups}</TableCell>
                  <TableCell className="text-right">
                    <Time value={payment.created_at} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="outline" disabled={busy || !payment.outcome || !payment.callback_url} onClick={() => onDeliverCallback(payment.payment_id)}>
                      Send callback
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

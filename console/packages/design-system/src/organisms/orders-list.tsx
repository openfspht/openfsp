import type { EventType, Money as MoneyValue, PaymentStatus } from "@workspace/types"

import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Mono, Money, Muted, StatusIndicator, Time } from "../atoms"

export interface OrderSummary {
  reference: string
  product: string
  amount: MoneyValue
  status: PaymentStatus
  created_at: string
  events: { id: string; type: EventType; sequence: number; received_at: string }[]
}

export function OrdersList({ orders }: { orders: OrderSummary[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Your orders</CardTitle>
        <CardDescription>Each status comes from a webhook whose signature the shop verified.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {orders.length === 0 && <EmptyText>No order yet.</EmptyText>}
        {orders.map((order) => (
          <div key={order.reference} className="space-y-2 border-b pb-4 last:border-b-0">
            <div className="flex items-center justify-between gap-4">
              <div className="min-w-0">
                <div className="font-medium">{order.product}</div>
                <Mono className="text-muted-foreground">{order.reference}</Mono>
              </div>
              <div className="text-right">
                <Money value={order.amount} className="block" />
                <StatusIndicator status={order.status} />
              </div>
            </div>
            {order.status === "pending" && <Muted className="block text-sm">Approve the payment on your phone.</Muted>}
            <div className="flex flex-wrap gap-2">
              {order.events.map((event) => (
                <Badge key={event.id} variant="outline">
                  #{event.sequence} {event.type} · verified <Time value={event.received_at} />
                </Badge>
              ))}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

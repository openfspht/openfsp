import type { EventType, Money as MoneyValue, NextAction, PaymentStatus } from "@workspace/types"

import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Tabular, Money, Muted, StatusIndicator, Time } from "../atoms"

export interface OrderSummary {
  reference: string
  product: string
  amount: MoneyValue
  provider: string
  status: PaymentStatus
  next_action?: NextAction
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
                <Tabular className="text-muted-foreground">
                  {order.reference}, {order.provider}
                </Tabular>
              </div>
              <div className="text-right">
                <Money value={order.amount} className="block" />
                <StatusIndicator status={order.status} />
              </div>
            </div>
            {order.status === "pending" && order.next_action?.type === "redirect" && (
              <a className="text-sm font-medium text-primary underline underline-offset-4" href={order.next_action.redirect_url}>
                Continue to {order.provider}
              </a>
            )}
            {order.status === "pending" && order.next_action?.type === "payer_approval" && (
              <Muted className="block text-sm">Approve the payment on your phone.</Muted>
            )}
            <div className="flex flex-wrap gap-2">
              {order.events.map((event) => (
                <Badge key={event.id} variant="outline">
                  #{event.sequence} {event.type}, verified at <Time value={event.received_at} />
                </Badge>
              ))}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

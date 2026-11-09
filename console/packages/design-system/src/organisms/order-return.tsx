import type { OrderSummary } from "./orders-list"
import { Card, CardContent, CardDescription, CardHeader, CardTitle, Mono, Money, StatusIndicator } from "../atoms"

const message: Record<OrderSummary["status"], string> = {
  pending: "Your payment is not settled yet. This page updates by itself.",
  succeeded: "Thank you, your payment went through.",
  failed: "The payment did not go through.",
  expired: "The payment window closed before the payment.",
  canceled: "The payment was canceled.",
}

export function OrderReturn({ order }: { order: OrderSummary }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{order.product}</CardTitle>
        <CardDescription>
          <Mono>{order.reference}</Mono>, status read from the gateway, not from the return link.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex items-center justify-between gap-4">
        <span className="text-sm">{message[order.status]}</span>
        <span className="text-right">
          <Money value={order.amount} className="block font-heading text-xl font-bold" />
          <StatusIndicator status={order.status} />
        </span>
      </CardContent>
    </Card>
  )
}

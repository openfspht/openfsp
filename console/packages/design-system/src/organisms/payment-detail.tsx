import type { PaymentDetail as Detail } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Heading, Mono, Money, Muted, Separator, StatusIndicator, Time } from "../atoms"
import { DeliveryItem, Field, TransitionItem } from "../molecules"

export function PaymentDetail({ detail }: { detail: Detail }) {
  const { payment } = detail
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-4 font-heading">
          <Mono className="min-w-0 truncate text-sm normal-case">{payment.id}</Mono>
          <span className="shrink-0">
            <StatusIndicator status={payment.status} />
          </span>
        </CardTitle>
        <CardDescription>
          <Money value={payment.amount} /> via {payment.provider}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <dl className="space-y-2">
          <Field label="Reference">{payment.reference}</Field>
          <Field label="Order id">
            <Mono>{payment.provider_order_id}</Mono>
          </Field>
          {payment.provider_reference && <Field label="Provider reference">{payment.provider_reference}</Field>}
          {payment.next_action && <Field label="Next action">{payment.next_action.type}</Field>}
          {payment.failure_reason && <Field label="Failure reason">{payment.failure_reason}</Field>}
          <Field label="Created">
            <Time value={payment.created_at} withDate />
          </Field>
          {payment.completed_at && (
            <Field label="Completed">
              <Time value={payment.completed_at} withDate />
            </Field>
          )}
        </dl>

        <Separator />

        <section className="space-y-3">
          <Heading>Lifecycle</Heading>
          <ol className="space-y-2">
            {detail.transitions.map((transition) => (
              <TransitionItem key={`${transition.to_status}-${transition.recorded_at}`} transition={transition} />
            ))}
          </ol>
          {detail.conflicts.map((conflict) => (
            <ErrorText key={conflict.recorded_at}>
              Conflict: {conflict.source} reported {conflict.reported.status} for {conflict.reported.amount} cents
            </ErrorText>
          ))}
        </section>

        <Separator />

        <section className="space-y-4">
          <Heading>Webhooks</Heading>
          {detail.events.length === 0 && <EmptyText>No event recorded.</EmptyText>}
          {detail.events.map((event) => (
            <div key={event.id} className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium">{event.type}</span>
                <Muted className="text-xs tabular-nums">sequence {event.sequence}</Muted>
              </div>
              {event.deliveries.length === 0 && <EmptyText className="text-xs">No endpoint subscribed.</EmptyText>}
              {event.deliveries.map((delivery) => (
                <DeliveryItem key={delivery.endpoint} delivery={delivery} />
              ))}
            </div>
          ))}
        </section>
      </CardContent>
    </Card>
  )
}

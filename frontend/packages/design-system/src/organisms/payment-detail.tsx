import type { AttestedStatus, PaymentDetail as Detail } from "@workspace/types"

import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Heading, Mono, Money, Muted, Separator, StatusIndicator, Time } from "../atoms"
import { AttestationActions, DeliveryItem, Field, TransitionItem } from "../molecules"

interface PaymentDetailProps {
  detail: Detail
  onAttest?: (status: AttestedStatus) => void
  attesting?: boolean
}

export function PaymentDetail({ detail, onAttest, attesting }: PaymentDetailProps) {
  const { payment } = detail
  return (
    <Card>
      <CardHeader>
        <CardTitle className="min-w-0 font-heading">
          <Mono className="block truncate text-sm normal-case">{payment.id}</Mono>
        </CardTitle>
        <CardAction>
          <StatusIndicator status={payment.status} />
        </CardAction>
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

        {detail.indeterminate.length > 0 && (
          <ErrorText>
            The creation had an unknown outcome at {payment.provider} and was not resent. Check with the operator, then attest.
          </ErrorText>
        )}

        {onAttest && payment.status === "pending" && (
          <section className="space-y-3">
            <Heading>Operator attestation</Heading>
            <Muted className="block text-sm">
              {detail.automatic_sources
                ? "The provider reports outcomes itself. Attest only with evidence such as a statement line."
                : "This provider has no automatic source: the outcome needs an attestation backed by evidence."}
            </Muted>
            <AttestationActions disabled={attesting} onAttest={onAttest} />
          </section>
        )}

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

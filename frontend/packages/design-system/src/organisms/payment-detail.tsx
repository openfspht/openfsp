import type { AttestedStatus, PaymentDetail as Detail } from "@workspace/types"

import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Heading, Mono, Money, Muted, Separator, StatusIndicator, Time } from "../atoms"
import { AttestationActions, DeliveryItem, Field, TransitionItem } from "../molecules"
import { useCopy } from "../copy"

interface PaymentDetailProps {
  detail: Detail
  onAttest?: (status: AttestedStatus) => void
  attesting?: boolean
}

export function PaymentDetail({ detail, onAttest, attesting }: PaymentDetailProps) {
  const { payment } = detail
  const copy = useCopy().paymentDetail
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
          <Money value={payment.amount} /> {copy.via} {payment.provider}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <dl className="space-y-2">
          <Field label={copy.reference}>{payment.reference}</Field>
          <Field label={copy.orderId}>
            <Mono>{payment.provider_order_id}</Mono>
          </Field>
          {payment.provider_reference && <Field label={copy.providerReference}>{payment.provider_reference}</Field>}
          {payment.next_action && <Field label={copy.nextAction}>{payment.next_action.type}</Field>}
          {payment.failure_reason && <Field label={copy.failureReason}>{payment.failure_reason}</Field>}
          <Field label={copy.created}>
            <Time value={payment.created_at} withDate />
          </Field>
          {payment.completed_at && (
            <Field label={copy.completed}>
              <Time value={payment.completed_at} withDate />
            </Field>
          )}
        </dl>

        {detail.indeterminate.length > 0 && (
          <ErrorText>
            {copy.indeterminate(payment.provider)}
          </ErrorText>
        )}

        {onAttest && payment.status === "pending" && (
          <section className="space-y-3">
            <Heading>{copy.attestationTitle}</Heading>
            <Muted className="block text-sm">
              {detail.automatic_sources ? copy.attestationAutomatic : copy.attestationManual}
            </Muted>
            <AttestationActions disabled={attesting} onAttest={onAttest} />
          </section>
        )}

        <Separator />

        <section className="space-y-3">
          <Heading>{copy.lifecycle}</Heading>
          <ol className="space-y-2">
            {detail.transitions.map((transition) => (
              <TransitionItem key={`${transition.to_status}-${transition.recorded_at}`} transition={transition} />
            ))}
          </ol>
          {detail.conflicts.map((conflict) => (
            <ErrorText key={conflict.recorded_at}>
              {copy.conflict(conflict.source, conflict.reported.status, conflict.reported.amount)}
            </ErrorText>
          ))}
        </section>

        <Separator />

        <section className="space-y-4">
          <Heading>{copy.webhooks}</Heading>
          {detail.events.length === 0 && <EmptyText>{copy.noEvent}</EmptyText>}
          {detail.events.map((event) => (
            <div key={event.id} className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium">{event.type}</span>
                <Muted className="text-xs tabular-nums">{copy.sequence} {event.sequence}</Muted>
              </div>
              {event.deliveries.length === 0 && <EmptyText className="text-xs">{copy.noEndpoint}</EmptyText>}
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

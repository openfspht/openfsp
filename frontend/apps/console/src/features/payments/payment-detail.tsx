import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  EmptyText,
  ErrorText,
  Field,
  Heading,
  Money,
  Muted,
  Separator,
  StatusIndicator,
  Tabular,
  Time,
} from "@workspace/design-system"
import type {
  AttestedStatus,
  PaymentDetail as Detail,
  EventView,
} from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

import { AttestationActions } from "./attestation-actions"
import { DeliveryItem } from "./delivery-item"
import { TransitionItem } from "./transition-item"

interface PaymentDetailProps {
  detail: Detail
  onAttest?: (status: AttestedStatus) => void
  attesting?: boolean
  providerName?: string
  actions?: React.ReactNode
}

export function PaymentDetail({
  detail,
  onAttest,
  attesting,
  providerName,
  actions,
}: PaymentDetailProps) {
  const { payment } = detail
  const copy = useI18n().t.paymentDetail
  return (
    <Card>
      <CardHeader>
        <CardTitle className="min-w-0 font-heading">
          <span className="block truncate">{payment.reference}</span>
          <Tabular className="block truncate text-sm font-normal tracking-normal text-muted-foreground normal-case">
            {payment.id}
          </Tabular>
        </CardTitle>
        <CardAction>
          <StatusIndicator status={payment.status} />
        </CardAction>
        <CardDescription className="space-y-3">
          <Money
            value={payment.amount}
            className="block font-heading text-2xl font-bold text-foreground"
          />
          <span className="block">
            {copy.via} {providerName ?? payment.provider}
          </span>
          {actions && <span className="flex flex-wrap gap-2">{actions}</span>}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <dl className="space-y-3">
          <Field label={copy.reference}>{payment.reference}</Field>
          <Field label={copy.orderId}>
            <Tabular>{payment.provider_order_id}</Tabular>
          </Field>
          {payment.provider_reference && (
            <Field label={copy.providerReference}>
              {payment.provider_reference}
            </Field>
          )}
          {payment.next_action && (
            <Field label={copy.nextAction}>{payment.next_action.type}</Field>
          )}
          {payment.failure_reason && (
            <Field label={copy.failureReason}>{payment.failure_reason}</Field>
          )}
          <Field label={copy.created}>
            <Time value={payment.created_at} />
          </Field>
          {payment.completed_at && (
            <Field label={copy.completed}>
              <Time value={payment.completed_at} />
            </Field>
          )}
        </dl>

        {detail.indeterminate.length > 0 && (
          <ErrorText>{copy.indeterminate(payment.provider)}</ErrorText>
        )}

        {onAttest && payment.status === "pending" && (
          <section className="space-y-3">
            <Heading>{copy.attestationTitle}</Heading>
            <Muted className="block text-sm">
              {detail.automatic_sources
                ? copy.attestationAutomatic
                : copy.attestationManual}
            </Muted>
            <AttestationActions disabled={attesting} onAttest={onAttest} />
          </section>
        )}

        <Separator />

        <section className="space-y-3">
          <Heading>{copy.lifecycle}</Heading>
          <ol className="space-y-3">
            {detail.transitions.map((transition) => (
              <TransitionItem
                key={`${transition.to_status}-${transition.recorded_at}`}
                transition={transition}
              />
            ))}
          </ol>
          {detail.conflicts.map((conflict) => (
            <ErrorText key={conflict.recorded_at}>
              {copy.conflict(
                conflict.source,
                conflict.reported.status,
                conflict.reported.amount
              )}
            </ErrorText>
          ))}
        </section>
      </CardContent>
    </Card>
  )
}

function PaymentEvents({ events }: { events: EventView[] }) {
  const copy = useI18n().t.paymentDetail
  if (events.length === 0) return <EmptyText>{copy.noEvent}</EmptyText>
  return (
    <div className="space-y-5">
      {events.map((event) => (
        <div
          key={event.id}
          className="space-y-2 border-b pb-4 last:border-b-0 last:pb-0"
        >
          <div className="flex items-center justify-between">
            <span className="font-medium">{event.type}</span>
            <Muted className="text-sm tabular-nums">
              {copy.sequence} {event.sequence}
            </Muted>
          </div>
          {event.deliveries.length === 0 && (
            <EmptyText className="text-sm">{copy.noEndpoint}</EmptyText>
          )}
          {event.deliveries.map((delivery) => (
            <DeliveryItem key={delivery.endpoint} delivery={delivery} />
          ))}
        </div>
      ))}
    </div>
  )
}

export function PaymentWebhooksCard({ events }: { events: EventView[] }) {
  const copy = useI18n().t.paymentDetail
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.webhooks}</CardTitle>
      </CardHeader>
      <CardContent>
        <PaymentEvents events={events} />
      </CardContent>
    </Card>
  )
}

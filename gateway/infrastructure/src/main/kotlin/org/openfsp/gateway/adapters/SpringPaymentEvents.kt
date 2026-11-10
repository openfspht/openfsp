package org.openfsp.gateway.adapters

import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.payment.spi.PaymentEvents
import org.openfsp.gateway.webhook.api.RecordPaymentEvents
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SpringPaymentEvents(
    private val publisher: ApplicationEventPublisher,
) : PaymentEvents {
    override fun publish(event: PaymentTransitioned) = publisher.publishEvent(event)
}

@Component
class PaymentEventsToWebhooks(
    private val recorder: RecordPaymentEvents,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun on(event: PaymentTransitioned) = recorder.record(event)
}

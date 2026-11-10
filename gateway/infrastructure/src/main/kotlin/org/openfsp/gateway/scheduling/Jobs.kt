package org.openfsp.gateway.scheduling

import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.openfsp.gateway.statement.api.ReconcileStatements
import org.openfsp.gateway.webhook.api.DeliverWebhooks
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

@Component
class WebhookDispatchJob(
    private val webhooks: DeliverWebhooks,
    private val workers: ExecutorService,
) {
    @Scheduled(fixedDelayString = "\${openfsp.webhooks.poll-interval:500ms}")
    fun run() = webhooks.claimDue(BATCH).forEach { delivery -> workers.submit { webhooks.deliver(delivery) } }

    private companion object {
        const val BATCH = 20
    }
}

@Component
class ReconciliationJob(
    private val payments: ReconcilePendingPayments,
) {
    @Scheduled(fixedDelayString = "\${openfsp.reconciliation-interval:30s}", initialDelayString = "\${openfsp.reconciliation-interval:30s}")
    fun run() {
        payments.reconcile(BATCH)
    }

    private companion object {
        const val BATCH = 50
    }
}

@Component
class SigningKeyRotationJob(
    private val rotation: RotateSigningKeys,
) {
    @Scheduled(fixedDelayString = "\${openfsp.webhooks.rotation-check-interval:1h}", initialDelayString = "\${openfsp.webhooks.rotation-check-interval:1h}")
    fun run() {
        rotation.current()
        rotation.rotateIfDue()
    }
}

@Component
class StatementJob(
    private val statements: ReconcileStatements,
) {
    @Scheduled(fixedDelayString = "\${openfsp.statement-interval:1m}", initialDelayString = "\${openfsp.statement-interval:1m}")
    fun run() = statements.fetchRecent()
}

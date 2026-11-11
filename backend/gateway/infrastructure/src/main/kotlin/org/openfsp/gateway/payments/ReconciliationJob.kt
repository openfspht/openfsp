package org.openfsp.gateway.payments

import org.openfsp.gateway.payment.api.ReconcilePendingPayments
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

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

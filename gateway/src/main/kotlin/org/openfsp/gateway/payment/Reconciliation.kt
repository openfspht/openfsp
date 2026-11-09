package org.openfsp.gateway.payment

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class Reconciliation(
    private val payments: Payments,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${openfsp.reconciliation-interval:30s}", initialDelayString = "\${openfsp.reconciliation-interval:30s}")
    fun run() {
        val reconciled = payments.reconcilePending(BATCH)
        if (reconciled > 0) log.info("Reconciled {} pending payments with their providers", reconciled)
    }

    private companion object {
        const val BATCH = 50
    }
}

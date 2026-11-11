package org.openfsp.gateway.statements

import org.openfsp.gateway.statement.api.ReconcileStatements
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StatementJob(
    private val statements: ReconcileStatements,
) {
    @Scheduled(fixedDelayString = "\${openfsp.statement-interval:1m}", initialDelayString = "\${openfsp.statement-interval:1m}")
    fun run() = statements.fetchRecent()
}

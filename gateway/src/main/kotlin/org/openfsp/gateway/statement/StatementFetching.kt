package org.openfsp.gateway.statement

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StatementFetching(
    private val statements: Statements,
) {
    @Scheduled(fixedDelayString = "\${openfsp.statement-interval:1m}", initialDelayString = "\${openfsp.statement-interval:1m}")
    fun run() = statements.fetchRecent()
}

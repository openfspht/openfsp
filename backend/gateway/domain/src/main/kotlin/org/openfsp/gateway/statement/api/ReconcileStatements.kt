package org.openfsp.gateway.statement.api

import java.time.LocalDate

interface ReconcileStatements {
    fun fetchRecent()

    fun fetch(
        provider: String,
        day: LocalDate,
    )
}

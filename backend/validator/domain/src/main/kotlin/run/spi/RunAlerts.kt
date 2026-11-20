package org.openfsp.validator.run.spi

import java.util.UUID

fun interface RunAlerts {
    fun stopped(
        id: UUID,
        cause: Exception,
    )
}

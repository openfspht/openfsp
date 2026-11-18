package org.openfsp.gateway.conformance.spi

import java.time.Duration

fun interface Deferrals {
    fun after(
        delay: Duration,
        action: () -> Unit,
    )
}

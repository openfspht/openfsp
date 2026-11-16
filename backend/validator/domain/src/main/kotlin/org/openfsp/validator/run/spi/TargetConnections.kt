package org.openfsp.validator.run.spi

import org.openfsp.validator.engine.spi.Transport
import java.net.URI
import java.time.Duration

interface TargetConnection :
    Transport,
    AutoCloseable {
    override fun close()
}

fun interface TargetConnections {
    fun open(
        baseUrl: URI,
        timeout: Duration,
    ): TargetConnection
}

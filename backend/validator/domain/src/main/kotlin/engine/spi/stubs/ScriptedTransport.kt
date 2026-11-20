package org.openfsp.validator.engine.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.Expectations
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.spi.Transport
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class ScriptedTransport(
    private val answers: Map<String, Pair<Int, String>> = emptyMap(),
) : Transport {
    val requests = CopyOnWriteArrayList<OutgoingRequest>()

    override fun send(request: OutgoingRequest): Exchange {
        requests += request
        val (status, body) = answers["${request.method} ${URI.create(request.url).rawPath}"] ?: (NOT_FOUND to """{"type":"not-found"}""")
        return Exchange(request, status, mapOf(Expectations.CONTENT_TYPE to listOf(Expectations.JSON)), body.toByteArray())
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}

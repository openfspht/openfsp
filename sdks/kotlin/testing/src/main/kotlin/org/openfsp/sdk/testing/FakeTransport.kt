package org.openfsp.sdk.testing

import org.openfsp.sdk.http.Transport
import org.openfsp.sdk.http.TransportRequest
import org.openfsp.sdk.http.TransportResponse
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class FakeTransport(
    vararg replies: Any,
) : Transport {
    private val replies = ConcurrentLinkedQueue<Any>(replies.toList())
    private val received = CopyOnWriteArrayList<TransportRequest>()

    val requests: List<TransportRequest> get() = received.toList()

    fun enqueue(vararg replies: Any) = apply { this.replies.addAll(replies) }

    override fun send(request: TransportRequest): TransportResponse {
        received += request
        return when (val reply = replies.poll() ?: throw AssertionError("FakeTransport has no reply left for ${request.method} ${request.uri.path}")) {
            is TransportResponse -> reply
            is RuntimeException -> throw reply
            else -> throw IllegalArgumentException("a reply is an TransportResponse or a RuntimeException, not ${reply.javaClass.simpleName}")
        }
    }
}

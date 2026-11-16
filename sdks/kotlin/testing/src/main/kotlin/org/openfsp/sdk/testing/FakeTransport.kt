package org.openfsp.sdk.testing

import org.openfsp.sdk.http.HttpRequest
import org.openfsp.sdk.http.HttpResponse
import org.openfsp.sdk.http.Transport
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class FakeTransport(
    vararg replies: Any,
) : Transport {
    private val replies = ConcurrentLinkedQueue<Any>(replies.toList())
    private val received = CopyOnWriteArrayList<HttpRequest>()

    val requests: List<HttpRequest> get() = received.toList()

    fun enqueue(vararg replies: Any) = apply { this.replies.addAll(replies) }

    override fun send(request: HttpRequest): HttpResponse {
        received += request
        return when (val reply = replies.poll() ?: throw AssertionError("FakeTransport has no reply left for ${request.method} ${request.uri.path}")) {
            is HttpResponse -> reply
            is RuntimeException -> throw reply
            else -> throw IllegalArgumentException("a reply is an HttpResponse or a RuntimeException, not ${reply.javaClass.simpleName}")
        }
    }
}

package org.openfsp.sdk.testing

import org.openfsp.sdk.http.Transport
import org.openfsp.sdk.http.TransportRequest
import org.openfsp.sdk.http.TransportResponse
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class FakeTransport(
    vararg responses: TransportResponse,
) : Transport {
    private val replies = ConcurrentLinkedQueue<Reply>(responses.map(Reply::Answer))
    private val received = CopyOnWriteArrayList<TransportRequest>()

    val requests: List<TransportRequest> get() = received.toList()

    fun enqueue(vararg responses: TransportResponse) = apply { replies.addAll(responses.map(Reply::Answer)) }

    fun enqueueFailure(vararg failures: RuntimeException) = apply { replies.addAll(failures.map(Reply::Failure)) }

    override fun send(request: TransportRequest): TransportResponse {
        received += request
        return when (val reply = replies.poll() ?: throw AssertionError("FakeTransport has no reply left for ${request.method} ${request.uri.path}")) {
            is Reply.Answer -> reply.response
            is Reply.Failure -> throw reply.failure
        }
    }

    private sealed interface Reply {
        class Answer(
            val response: TransportResponse,
        ) : Reply

        class Failure(
            val failure: RuntimeException,
        ) : Reply
    }
}

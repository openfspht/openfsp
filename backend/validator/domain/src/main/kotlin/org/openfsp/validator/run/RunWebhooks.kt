package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Reply
import org.openfsp.validator.engine.WebhookRecorder
import org.openfsp.validator.run.api.ReceiveRunWebhooks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DomainService
class RunWebhooks(
    private val settings: RunSettings,
    private val sessions: RunSessions,
) : ReceiveRunWebhooks {
    private val recorders = ConcurrentHashMap<UUID, WebhookRecorder>()

    fun urlOf(id: UUID): String = "${settings.publicUrl.trimEnd('/')}${pathOf(id)}"

    fun open(id: UUID): WebhookRecorder = recorders.computeIfAbsent(id) { WebhookRecorder(urlOf(it), settings.webhookCapacity) }

    fun find(id: UUID): WebhookRecorder? = recorders[id]

    override fun knows(id: UUID): Boolean = recorders.containsKey(id)

    override fun receive(
        id: UUID,
        method: String,
        rest: String,
        headers: Map<String, List<String>>,
        body: ByteArray,
    ): Reply? {
        val recorder = find(id) ?: return null
        return recorder.receive(method, recorder.url + rest, headers, body)
    }

    fun forgetEnded() {
        recorders.keys.filter { sessions.find(it) == null }.forEach(recorders::remove)
    }

    companion object {
        fun pathOf(id: UUID): String = "/api/runs/$id/webhooks"
    }
}

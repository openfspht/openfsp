package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Pointers
import org.openfsp.validator.engine.Reply
import org.openfsp.validator.engine.WebhookRecorder
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.run.api.ReceiveRunWebhooks
import org.openfsp.validator.run.api.RunState
import org.openfsp.validator.run.api.RunStates
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DomainService
class RunWebhooks(
    private val settings: RunSettings,
    private val sessions: RunSessions,
    private val states: RunStates,
    private val json: JsonCodec,
    private val pointers: Pointers,
) : ReceiveRunWebhooks {
    private val recorders = ConcurrentHashMap<UUID, WebhookRecorder>()

    fun urlOf(id: UUID): String = "${settings.publicUrl.trimEnd('/')}${settings.runsPath}/$id/webhooks"

    fun open(id: UUID): WebhookRecorder = recorders.computeIfAbsent(id) { WebhookRecorder(urlOf(it), json, pointers, settings.webhookCapacity, settings.webhookBytes) }

    fun find(id: UUID): WebhookRecorder? = recorders[id]

    override fun knows(id: UUID): Boolean = recorders.containsKey(id)

    override fun receive(
        id: UUID,
        method: String,
        rest: String,
        headers: Map<String, List<String>>,
        body: ByteArray,
    ): Reply? {
        val recorder = find(id)?.takeIf { states.state(id) == RunState.RUNNING } ?: return null
        return recorder.receive(method, recorder.url + rest, headers, body)
    }

    fun forgetEnded() {
        recorders.keys.filter { sessions.find(it) == null }.forEach(recorders::remove)
    }
}

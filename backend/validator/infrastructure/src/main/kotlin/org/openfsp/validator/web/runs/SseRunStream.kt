package org.openfsp.validator.web.runs

import org.openfsp.validator.run.RunEvent
import org.openfsp.validator.run.spi.RunEventStream
import org.openfsp.validator.shared.json.ValidatorJson
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

class SseRunStream(
    val emitter: SseEmitter,
) : RunEventStream {
    override fun send(events: List<RunEvent>): Boolean =
        try {
            events.forEach { event -> emitter.send(SseEmitter.event().id(event.id.toString()).name(event.name).data(ValidatorJson.documents.writeValueAsString(event.payload), MediaType.TEXT_PLAIN)) }
            true
        } catch (_: IOException) {
            emitter.complete()
            false
        } catch (_: IllegalStateException) {
            false
        }

    override fun complete() = emitter.complete()

    override fun onClose(action: () -> Unit) {
        emitter.onCompletion(action)
        emitter.onTimeout(action)
        emitter.onError { action() }
    }
}

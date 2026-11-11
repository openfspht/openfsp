package org.openfsp.validator.web.runs

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.validator.engine.WebhookRecorder
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RunWebhooks(
    private val properties: ValidatorProperties,
    private val sessions: RunSessions,
) {
    private val recorders = ConcurrentHashMap<UUID, WebhookRecorder>()

    fun urlOf(id: UUID): String = "${properties.publicUrl.trimEnd('/')}${pathOf(id)}"

    fun open(id: UUID): WebhookRecorder = recorders.computeIfAbsent(id) { WebhookRecorder(urlOf(it), properties.webhookCapacity) }

    fun find(id: UUID): WebhookRecorder? = recorders[id]

    @Scheduled(fixedDelayString = "\${openfsp.validator.session-sweep-interval:1m}")
    fun forgetEnded() {
        recorders.keys.filter { sessions.find(it) == null }.forEach(recorders::remove)
    }

    companion object {
        fun pathOf(id: UUID): String = "/api/runs/$id/webhooks"
    }
}

@RestController
class RunWebhooksController(
    private val webhooks: RunWebhooks,
) {
    @RequestMapping("/api/runs/{id}/webhooks", "/api/runs/{id}/webhooks/**")
    fun receive(
        @PathVariable id: UUID,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val recorder = webhooks.find(id) ?: return ResponseEntity.notFound().build()
        val body = WebhookRecorder.readBounded(request.inputStream) ?: return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()
        val rest = request.requestURI.removePrefix(request.contextPath).removePrefix(RunWebhooks.pathOf(id))
        val target = recorder.url + rest + (request.queryString?.let { "?$it" } ?: "")
        val headers = request.headerNames.toList().associateWith { request.getHeaders(it).toList() }
        val reply = recorder.receive(request.method, target, headers, body)
        if (!reply.delay.isZero) Thread.sleep(reply.delay)
        val response = HttpHeaders()
        reply.headers.forEach { (name, value) -> response.add(name, value) }
        return ResponseEntity.status(reply.status).headers(response).body(reply.body.takeIf { it.isNotEmpty() })
    }
}

package org.openfsp.validator.runs.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.validator.runs.http.WebhookBodies
import org.openfsp.validator.run.api.ReceiveRunWebhooks
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RunWebhooksController(
    private val webhooks: ReceiveRunWebhooks,
) {
    @RequestMapping("${RunsController.BASE}/{id}$WEBHOOKS", "${RunsController.BASE}/{id}$WEBHOOKS/**")
    fun receive(
        @PathVariable id: UUID,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        if (!webhooks.knows(id)) return ResponseEntity.notFound().build()
        val body = WebhookBodies.readBounded(request.inputStream) ?: return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()
        val rest = request.requestURI.removePrefix(request.contextPath).removePrefix("${RunsController.BASE}/$id$WEBHOOKS") + (request.queryString?.let { "?$it" } ?: "")
        val headers = request.headerNames.toList().associateWith { request.getHeaders(it).toList() }
        val reply = webhooks.receive(id, request.method, rest, headers, body) ?: return ResponseEntity.notFound().build()
        if (!reply.delay.isZero) Thread.sleep(reply.delay)
        val response = HttpHeaders()
        reply.headers.forEach { (name, value) -> response.add(name, value) }
        return ResponseEntity.status(reply.status).headers(response).body(reply.body.takeIf { it.isNotEmpty() })
    }

    private companion object {
        const val WEBHOOKS = "/webhooks"
    }
}

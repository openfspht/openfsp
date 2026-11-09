package org.openfsp.gateway.payment

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.provider.Providers
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CallbackController(
    private val providers: Providers,
    private val payments: Payments,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/callbacks/{provider}")
    fun receive(
        @PathVariable provider: String,
        @RequestBody body: ByteArray,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val adapter = providers[provider] ?: return ResponseEntity.notFound().build()
        val report = adapter.verifiedCallback(request::getHeader, body)
        if (report == null) {
            log.warn("Rejected a callback from {}: signature not verified", provider)
            return ResponseEntity.status(401).build()
        }
        payments.onCallback(provider, report) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }
}

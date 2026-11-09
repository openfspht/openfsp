package org.openfsp.gateway.payment

import org.openfsp.gateway.provider.Providers
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class ReturnController(
    private val providers: Providers,
    private val repository: PaymentRepository,
    private val payments: Payments,
) {
    @GetMapping("/return/{provider}")
    fun payerReturn(
        @PathVariable provider: String,
        @RequestParam("order_id", required = false) orderId: String?,
    ): ResponseEntity<Void> {
        val adapter = providers[provider] ?: return ResponseEntity.notFound().build()
        val payment = orderId?.let { repository.findByProviderAndProviderOrderId(provider, it) } ?: return ResponseEntity.notFound().build()
        val target = payment.returnUrl ?: return ResponseEntity.notFound().build()
        if (adapter.descriptor.hasLookup) runCatching { payments.synchronize(payment.id) }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build()
    }
}

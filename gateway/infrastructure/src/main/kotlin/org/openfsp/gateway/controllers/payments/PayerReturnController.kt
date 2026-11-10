package org.openfsp.gateway.controllers.payments

import org.openfsp.gateway.payment.api.SynchronizeAPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.spi.Providers
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PayerReturnController(
    private val providers: Providers,
    private val payments: Payments,
    private val synchronizeAPayment: SynchronizeAPayment,
) {
    @GetMapping("/return/{provider}")
    fun payerReturn(
        @PathVariable provider: String,
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<Void> {
        val operations = providers[provider] ?: return ResponseEntity.notFound().build()
        val orderId = runCatching { operations.returnedOrder(parameters) }.getOrNull()
        val payment = orderId?.let { payments.withOrder(provider, it) } ?: return ResponseEntity.notFound().build()
        val target = payment.returnUrl ?: return ResponseEntity.notFound().build()
        if (operations.descriptor.hasLookup) runCatching { synchronizeAPayment.synchronize(payment.id) }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(target).build()
    }
}

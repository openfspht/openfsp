package org.openfsp.gateway.payments.web

import org.openfsp.gateway.payment.api.ReturnPayer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PayerReturnController(
    private val returnPayer: ReturnPayer,
) {
    @GetMapping("/return/{provider}")
    fun payerReturn(
        @PathVariable provider: String,
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<Void> {
        val target = returnPayer.returnTarget(provider, parameters) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(target).build()
    }
}

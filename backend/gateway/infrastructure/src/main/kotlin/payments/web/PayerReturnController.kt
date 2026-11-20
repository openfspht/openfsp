package org.openfsp.gateway.payments.web

import org.openfsp.gateway.payment.api.ReturnPayer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class PayerReturnController(
    private val returnPayer: ReturnPayer,
) {
    @GetMapping("/return/{provider}")
    fun payerReturn(
        @PathVariable provider: String,
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<Void> = redirect(returnPayer.returnTarget(provider, null, parameters))

    @GetMapping("/return/{provider}/accounts/{account}")
    fun payerReturnOfAccount(
        @PathVariable provider: String,
        @PathVariable account: String,
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<Void> = redirect(returnPayer.returnTarget(provider, account, parameters))

    private fun redirect(target: URI?): ResponseEntity<Void> = target?.let { ResponseEntity.status(HttpStatus.SEE_OTHER).location(it).build() } ?: ResponseEntity.notFound().build()
}

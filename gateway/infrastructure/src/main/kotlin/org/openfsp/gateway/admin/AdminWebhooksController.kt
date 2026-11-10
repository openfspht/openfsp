package org.openfsp.gateway.admin

import org.openfsp.gateway.persistence.webhook.JpaDeliveries
import org.openfsp.gateway.persistence.webhook.JpaEndpointHealth
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhooks.WebhookSigners
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class EndpointResource(
    val name: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val pending: Long,
    val disabledAt: Instant?,
    val reason: String?,
)

data class RotatedKeyResource(
    val kid: String,
)

@RestController
@RequestMapping("/admin/webhooks")
class AdminWebhooksController(
    private val subscriptions: Subscriptions,
    private val deliveries: JpaDeliveries,
    private val health: JpaEndpointHealth,
    private val rotation: RotateSigningKeys,
    private val signers: WebhookSigners,
) {
    @GetMapping("/endpoints")
    fun endpoints(): org.openfsp.gateway.controllers.payments.Collection<EndpointResource> =
        org.openfsp.gateway.controllers.payments.Collection(
            subscriptions.all().map { subscription ->
                val state = health.state(subscription.name)
                EndpointResource(
                    subscription.name,
                    subscription.url.toString(),
                    subscription.events.sorted(),
                    subscription.enabled,
                    deliveries.pending(subscription.name),
                    state?.disabledAt,
                    state?.reason,
                )
            },
        )

    @PostMapping("/endpoints/{name}/enable")
    fun enable(
        @PathVariable name: String,
    ): ResponseEntity<Void> {
        if (subscriptions.all().none { it.name == name }) return ResponseEntity.notFound().build()
        health.enable(name)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/keys/rotate")
    fun rotate(
        @RequestParam(defaultValue = "false") immediate: Boolean,
    ): RotatedKeyResource = RotatedKeyResource(rotation.rotate(immediate)).also { signers.forget() }
}

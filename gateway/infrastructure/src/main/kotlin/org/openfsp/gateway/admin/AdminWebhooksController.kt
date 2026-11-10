package org.openfsp.gateway.admin

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.controllers.payments.Collection
import org.openfsp.gateway.controllers.webhooks.JwkMapper
import org.openfsp.gateway.controllers.webhooks.JwkResource
import org.openfsp.gateway.webhook.api.ManageSubscriptions
import org.openfsp.gateway.webhooks.WebhookProperties
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import java.net.URI
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
    val managed: Boolean,
    val pending: Long,
    val disabledAt: Instant?,
    val reason: String?,
)

data class EndpointRequest(
    val name: String,
    val url: String,
    val events: Set<String>,
)

data class SigningKeyResource(
    val kid: String,
    val status: String,
    val activatesAt: Instant,
    val retiredAt: Instant?,
    val publicKey: JwkResource,
)

data class SigningKeysResource(
    val keys: List<SigningKeyResource>,
    val rotateEveryDays: Long?,
    val nextRotationAt: Instant?,
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
    private val registry: ManageSubscriptions,
    private val jwks: JwkMapper,
    private val properties: WebhookProperties,
) {
    @GetMapping("/endpoints")
    fun endpoints(): Collection<EndpointResource> =
        Collection(
            subscriptions.all().map { subscription ->
                val state = health.state(subscription.name)
                EndpointResource(
                    subscription.name,
                    subscription.url.toString(),
                    subscription.events.sorted(),
                    subscription.enabled,
                    subscription.managed,
                    deliveries.pending(subscription.name),
                    state?.disabledAt,
                    state?.reason,
                )
            },
        )

    @PostMapping("/endpoints")
    fun add(
        @RequestBody request: EndpointRequest,
    ): ResponseEntity<Void> {
        val url = runCatching { URI(request.url.trim()) }.getOrElse { throw OpenFspException.invalid("url", "is not a URL") }
        registry.add(request.name.trim(), url, request.events)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/endpoints/{name}")
    fun remove(
        @PathVariable name: String,
    ): ResponseEntity<Void> = if (registry.remove(name)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/endpoints/{name}/enable")
    fun enable(
        @PathVariable name: String,
    ): ResponseEntity<Void> {
        if (subscriptions.all().none { it.name == name }) return ResponseEntity.notFound().build()
        health.enable(name)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/keys")
    fun keys(): SigningKeysResource {
        rotation.current()
        return SigningKeysResource(
            keys = rotation.published().map { SigningKeyResource(it.kid, it.status.wire, it.activatesAt, it.retiredAt, jwks.toResource(it)) },
            rotateEveryDays = properties.rotateKeyEvery.toDays().takeIf { it > 0 },
            nextRotationAt = rotation.nextRotationAt(),
        )
    }

    @PostMapping("/keys/rotate")
    fun rotate(
        @RequestParam(defaultValue = "false") immediate: Boolean,
    ): RotatedKeyResource = RotatedKeyResource(rotation.rotate(immediate)).also { signers.forget() }
}

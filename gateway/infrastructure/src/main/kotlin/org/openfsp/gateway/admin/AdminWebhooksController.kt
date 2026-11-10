package org.openfsp.gateway.admin

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.admin.search.PageQuery
import org.openfsp.gateway.admin.search.PageResource
import org.openfsp.gateway.admin.search.SortQuery
import org.openfsp.gateway.admin.search.pageOf
import org.openfsp.gateway.admin.search.sortedBy
import org.openfsp.gateway.webhook.api.ManageSubscriptions
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import java.net.URI
import org.openfsp.gateway.webhooks.persistence.JpaDeliveries
import org.openfsp.gateway.webhooks.persistence.EndpointStateJpaEntity
import org.openfsp.gateway.webhooks.persistence.JpaEndpointHealth
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhooks.WebhookSigners
import org.openfsp.protocol.FieldErrorCode
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
) {
    @GetMapping("/endpoints")
    fun endpoints(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<EndpointResource> {
        val needle = q.blankToNull()?.lowercase()
        val matching = subscriptions.all().filter { needle == null || needle in it.name.lowercase() || needle in it.url.toString().lowercase() }
        val ordered = matching.map { it to health.state(it.name) }.sortedBy(SortQuery.of(sort, order), ORDER, NAME)
        val shown = pageOf(ordered, PageQuery(page, size))
        return PageResource(shown.data.map { (subscription, state) -> resource(subscription, state) }, shown.page)
    }

    private fun resource(
        subscription: Subscription,
        state: EndpointStateJpaEntity?,
    ) = EndpointResource(
        subscription.name,
        subscription.url.toString(),
        subscription.events.sorted(),
        subscription.enabled,
        subscription.managed,
        deliveries.pending(subscription.name),
        state?.disabledAt,
        state?.reason,
    )

    @PostMapping("/endpoints")
    fun add(
        @RequestBody request: EndpointRequest,
    ): ResponseEntity<Void> {
        val url = runCatching { URI(request.url.trim()) }.getOrElse { throw OpenFspException.invalid("url", "is not a URL", FieldErrorCode.MALFORMED) }
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

    @PostMapping("/keys/rotate")
    fun rotate(
        @RequestParam(defaultValue = "false") immediate: Boolean,
    ): RotatedKeyResource = RotatedKeyResource(rotation.rotate(immediate)).also { signers.forget() }

    private companion object {
        const val NAME = "name"
        private val byName = compareBy<Pair<Subscription, EndpointStateJpaEntity?>> { it.first.name.lowercase() }
        val ORDER: Map<String, Comparator<Pair<Subscription, EndpointStateJpaEntity?>>> =
            mapOf(
                NAME to byName,
                "url" to compareBy { it.first.url.toString() },
                "status" to compareBy<Pair<Subscription, EndpointStateJpaEntity?>> { (subscription, state) -> if (state?.disabledAt != null) 2 else if (subscription.enabled) 0 else 1 }.then(byName),
            )
    }
}

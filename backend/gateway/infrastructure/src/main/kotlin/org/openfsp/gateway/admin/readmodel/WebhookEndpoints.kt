package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhooks.persistence.DeliveryJpaRepository
import org.openfsp.gateway.webhooks.persistence.EndpointStateJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class EndpointResource(
    val account: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val managed: Boolean,
    val pending: Long,
    val disabledAt: Instant?,
    val reason: String?,
)

@Component
class WebhookEndpoints(
    private val subscriptions: Subscriptions,
    private val deliveries: DeliveryJpaRepository,
    private val states: EndpointStateJpaRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        account: String?,
        query: String?,
        page: PageQuery,
        sort: SortQuery,
    ): PageResource<EndpointResource> {
        val needle = query?.lowercase()
        val matching = subscriptions.all().filter { account == null || it.account == account }.filter { needle == null || needle in it.name.lowercase() || needle in it.url.toString().lowercase() }
        val resources =
            matching.map { subscription ->
                val state = states.findById(subscription.name).orElse(null)
                EndpointResource(
                    subscription.account,
                    subscription.name,
                    subscription.url.toString(),
                    subscription.events.sorted(),
                    subscription.enabled,
                    subscription.managed,
                    deliveries.countByEndpointAndStatus(subscription.name, PENDING),
                    state?.disabledAt,
                    state?.reason,
                )
            }
        return pageOf(resources.sortedBy(sort, ORDER, NAME), page)
    }

    private companion object {
        const val NAME = "name"
        const val PENDING = "pending"
        private val byName = compareBy<EndpointResource> { it.name.lowercase() }
        val ORDER: Map<String, Comparator<EndpointResource>> =
            mapOf(
                NAME to byName,
                "url" to compareBy { it.url },
                "status" to compareBy<EndpointResource> { if (it.disabledAt != null) 2 else if (it.enabled) 0 else 1 }.then(byName),
            )
    }
}

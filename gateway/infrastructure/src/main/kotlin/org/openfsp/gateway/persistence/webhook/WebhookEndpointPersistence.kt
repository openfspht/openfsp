package org.openfsp.gateway.persistence.webhook

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant

@Entity
@Table(name = "webhook_endpoints")
class WebhookEndpointJpaEntity(
    @Id val name: String,
    val url: String,
    val events: String,
    val enabled: Boolean,
    val createdAt: Instant,
)

interface WebhookEndpointJpaRepository : JpaRepository<WebhookEndpointJpaEntity, String> {
    fun findByOrderByCreatedAt(): List<WebhookEndpointJpaEntity>
}

@Component
class WebhookEndpointMapper {
    fun toDomain(entity: WebhookEndpointJpaEntity) =
        Subscription(entity.name, URI(entity.url), entity.events.split(SEPARATOR).filter { it.isNotBlank() }.toSet(), entity.enabled, managed = true)

    fun toEntity(
        subscription: Subscription,
        at: Instant,
    ) = WebhookEndpointJpaEntity(subscription.name, subscription.url.toString(), subscription.events.sorted().joinToString(SEPARATOR), subscription.enabled, at)

    private companion object {
        const val SEPARATOR = " "
    }
}

@Component
class JpaSubscriptionStore(
    private val repository: WebhookEndpointJpaRepository,
    private val mapper: WebhookEndpointMapper,
    private val calendar: BusinessCalendar,
) : SubscriptionStore {
    @Transactional(readOnly = true)
    override fun all(): List<Subscription> = repository.findByOrderByCreatedAt().map(mapper::toDomain)

    @Transactional
    override fun save(subscription: Subscription) {
        repository.save(mapper.toEntity(subscription, calendar.now()))
    }

    @Transactional
    override fun delete(name: String): Boolean {
        if (!repository.existsById(name)) return false
        repository.deleteById(name)
        return true
    }
}

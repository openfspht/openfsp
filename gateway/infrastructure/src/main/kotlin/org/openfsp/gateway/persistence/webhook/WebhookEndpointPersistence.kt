package org.openfsp.gateway.persistence.webhook

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
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
) : Persistable<String> {
    override fun getId(): String = name

    @Transient
    override fun isNew(): Boolean = true
}

interface WebhookEndpointJpaRepository : JpaRepository<WebhookEndpointJpaEntity, String> {
    fun findByOrderByCreatedAt(): List<WebhookEndpointJpaEntity>

    @Modifying
    @Query("delete from WebhookEndpointJpaEntity e where e.name = :name")
    fun deleteByName(name: String): Int
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
        try {
            repository.saveAndFlush(mapper.toEntity(subscription, calendar.now()))
        } catch (_: DataIntegrityViolationException) {
            throw OpenFspException.invalid("name", "is already used by another webhook")
        }
    }

    @Transactional
    override fun delete(name: String): Boolean = repository.deleteByName(name) > 0
}

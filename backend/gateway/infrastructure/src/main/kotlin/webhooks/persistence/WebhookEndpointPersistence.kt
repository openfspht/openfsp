package org.openfsp.gateway.webhooks.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.DuplicateSubscription
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.net.URI
import java.time.Duration
import java.time.Instant

@Entity
@Table(name = "webhook_endpoints")
class WebhookEndpointJpaEntity(
    @Id val name: String,
    val accountId: String,
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
        Subscription(entity.accountId, entity.name, URI(entity.url), entity.events.split(SEPARATOR).filter { it.isNotBlank() }.toSet(), entity.enabled, managed = true)

    fun toEntity(
        subscription: Subscription,
        at: Instant,
    ) = WebhookEndpointJpaEntity(subscription.name, subscription.account, subscription.url.toString(), subscription.events.sorted().joinToString(SEPARATOR), subscription.enabled, at)

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
    @Volatile private var snapshot: Snapshot? = null

    override fun all(): List<Subscription> {
        val now = calendar.now()
        snapshot?.takeIf { it.takenAt.plus(SNAPSHOT_TTL).isAfter(now) }?.let { return it.subscriptions }
        return repository.findByOrderByCreatedAt().map(mapper::toDomain).also { snapshot = Snapshot(now, it) }
    }

    @Transactional
    override fun save(subscription: Subscription) {
        try {
            repository.saveAndFlush(mapper.toEntity(subscription, calendar.now()))
            forgetSnapshot()
        } catch (_: DataIntegrityViolationException) {
            throw DuplicateSubscription()
        }
    }

    @Transactional
    override fun delete(name: String): Boolean = (repository.deleteByName(name) > 0).also { forgetSnapshot() }

    private fun forgetSnapshot() {
        snapshot = null
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        snapshot = null
                    }
                },
            )
        }
    }

    private class Snapshot(
        val takenAt: Instant,
        val subscriptions: List<Subscription>,
    )

    private companion object {
        val SNAPSHOT_TTL: Duration = Duration.ofSeconds(5)
    }
}

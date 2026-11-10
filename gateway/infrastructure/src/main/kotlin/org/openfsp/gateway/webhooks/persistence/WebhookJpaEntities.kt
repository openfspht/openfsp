package org.openfsp.gateway.webhooks.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "events")
class EventJpaEntity(
    @Id val id: String,
    val paymentId: String,
    val type: String,
    val sequence: Int,
    val previousStatus: String?,
    val createdAt: Instant,
    val body: ByteArray,
)

@Entity
@Table(name = "webhook_deliveries")
class DeliveryJpaEntity(
    val eventId: String,
    val endpoint: String,
    val url: String,
    var status: String,
    var attempts: Int,
    var nextAttemptAt: Instant?,
    var updatedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

@Entity
@Table(name = "webhook_attempts")
class AttemptJpaEntity(
    val deliveryId: Long,
    val attempt: Int,
    val startedAt: Instant,
    val statusCode: Int?,
    val error: String?,
    val durationMs: Int,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

@Entity
@Table(name = "webhook_endpoint_state")
class EndpointStateJpaEntity(
    @Id val endpoint: String,
    var consecutiveAbandoned: Int = 0,
    var disabledAt: Instant? = null,
    var reason: String? = null,
)

@Entity
@Table(name = "webhook_signing_keys")
class SigningKeyJpaEntity(
    @Id val kid: String,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val createdAt: Instant,
    val activatesAt: Instant,
    var retiredAt: Instant? = null,
)

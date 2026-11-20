package org.openfsp.gateway.webhooks.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.protocol.webhook.NonceStore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Entity
@Table(name = "webhook_nonces")
class WebhookNonceJpaEntity(
    @Id var nonce: String,
    var expiresAt: Instant,
)

interface WebhookNonceRepository : JpaRepository<WebhookNonceJpaEntity, String> {
    @Modifying
    @Query(
        value = """
            INSERT INTO webhook_nonces (nonce, expires_at) VALUES (:nonce, :until)
            ON CONFLICT (nonce) DO UPDATE SET expires_at = EXCLUDED.expires_at WHERE webhook_nonces.expires_at < :now
        """,
        nativeQuery = true,
    )
    fun reserve(
        nonce: String,
        until: Instant,
        now: Instant,
    ): Int

    @Modifying
    @Query("DELETE FROM WebhookNonceJpaEntity n WHERE n.nonce = :nonce")
    fun release(nonce: String): Int

    @Modifying
    @Query("DELETE FROM WebhookNonceJpaEntity n WHERE n.expiresAt < :now")
    fun deleteExpiredBefore(now: Instant): Int
}

@Component
class JpaNonceStore(
    private val nonces: WebhookNonceRepository,
    private val calendar: BusinessCalendar,
) : NonceStore {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun reserve(
        nonce: String,
        until: Instant,
    ): Boolean = nonces.reserve(nonce, until, calendar.now()) == 1

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun release(nonce: String) {
        nonces.release(nonce)
    }

    @Transactional
    fun purgeExpired(): Int = nonces.deleteExpiredBefore(calendar.now())
}

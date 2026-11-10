package org.openfsp.gateway.persistence.webhook

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
            insert into webhook_nonces (nonce, expires_at) values (:nonce, :until)
            on conflict (nonce) do update set expires_at = excluded.expires_at where webhook_nonces.expires_at < :now
        """,
        nativeQuery = true,
    )
    fun reserve(
        nonce: String,
        until: Instant,
        now: Instant,
    ): Int

    @Modifying
    @Query("delete from WebhookNonceJpaEntity n where n.nonce = :nonce")
    fun release(nonce: String): Int

    @Modifying
    @Query("delete from WebhookNonceJpaEntity n where n.expiresAt < :now")
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

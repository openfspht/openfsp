package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhook.spi.SigningKeyStore
import java.time.Duration
import java.time.Instant

data class SigningKey(
    val kid: String,
    val activatesAt: Instant,
    val retiredAt: Instant?,
) {
    fun isActiveAt(instant: Instant): Boolean = retiredAt == null && !activatesAt.isAfter(instant)

    fun isPublishedAt(instant: Instant): Boolean = retiredAt == null || retiredAt.isAfter(instant.minus(SigningKeyRotation.RETIRED_PUBLICATION))
}

enum class KeyStatus(
    val wire: String,
) {
    ACTIVE("active"),
    RETIRED("retired"),
}

data class PublishedSigningKey(
    val kid: String,
    val status: KeyStatus,
    val activatesAt: Instant,
    val retiredAt: Instant?,
)

data class KeyRotationPolicy(
    val rotateEvery: Duration?,
)

@DomainService
class SigningKeyRotation(
    private val store: SigningKeyStore,
    private val transactions: Transactions,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
    private val policy: KeyRotationPolicy,
) : RotateSigningKeys {
    override fun current(): String =
        transactions.inTransaction {
            val now = calendar.now()
            val keys = store.all().takeIf { it.signingAt(now) != null } ?: store.lockKeys().let { store.all() }
            val signing = keys.signingAt(now) ?: return@inTransaction store.create(now)
            keys.filter { it.isActiveAt(now) && it.kid != signing.kid }.forEach { store.retire(it.kid, now) }
            signing.kid
        }

    override fun published(): List<PublishedSigningKey> {
        val now = calendar.now()
        val keys = store.all()
        val signing = keys.signingAt(now)
        return keys
            .map { if (it.retiredAt == null && signing != null && it.kid != signing.kid && it.isActiveAt(now)) it.copy(retiredAt = signing.activatesAt) else it }
            .filter { it.isPublishedAt(now) }
            .sortedByDescending { it.activatesAt }
            .map { PublishedSigningKey(it.kid, if (it.retiredAt == null) KeyStatus.ACTIVE else KeyStatus.RETIRED, it.activatesAt, it.retiredAt) }
    }

    private fun List<SigningKey>.signingAt(now: Instant): SigningKey? = filter { it.isActiveAt(now) }.maxByOrNull { it.activatesAt }

    override fun nextRotationAt(): Instant? {
        val every = policy.rotateEvery ?: return null
        val newest = store.all().filter { it.retiredAt == null }.maxByOrNull { it.activatesAt } ?: return null
        return newest.activatesAt.plus(every)
    }

    override fun rotateIfDue(): String? =
        transactions.inTransaction {
            if (!store.lockRotation()) return@inTransaction null
            val activatesAt = nextRotationAt() ?: return@inTransaction null
            if (calendar.now().isBefore(activatesAt.minus(PRE_PUBLICATION))) null else rotate(immediate = false)
        }

    override fun rotate(immediate: Boolean): String =
        transactions.inTransaction {
            val now = calendar.now()
            val previous = store.all()
            val kid = store.create(if (immediate) now else now.plus(PRE_PUBLICATION))
            if (immediate) {
                previous.forEach { store.withdraw(it.kid) }
                signals.signal(kid, "webhook signing key rotated immediately: previous keys are withdrawn")
            } else {
                signals.signal(kid, "webhook signing key published, it signs from ${now.plus(PRE_PUBLICATION)}")
            }
            kid
        }

    companion object {
        val PRE_PUBLICATION: Duration = Duration.ofHours(24)
        val RETIRED_PUBLICATION: Duration = Duration.ofHours(24)
    }
}

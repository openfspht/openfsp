package org.openfsp.gateway.conformance

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.conformance.api.PrepareConformance
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.api.ManageSubscriptions
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentLinkedQueue

@DomainService
class ConformancePreparation(
    private val keys: ManageKeys,
    private val endpoints: ManageSubscriptions,
    private val subscriptions: Subscriptions,
    private val capabilities: DescribeCapabilities,
    private val calendar: BusinessCalendar,
    private val settings: ConformanceSettings,
    private val scripts: PaymentScripts,
) : PrepareConformance {
    private val random = SecureRandom()
    private val active = ConcurrentLinkedQueue<Instant>()

    override fun prepare(webhookUrl: URI?): ConformanceFixtures {
        val now = calendar.now()
        val expiresAt = reserve(now)
        val name = "$PREFIX${expiresAt.epochSecond}-${HexFormat.of().formatHex(ByteArray(SUFFIX_BYTES).also(random::nextBytes))}"
        val everything = Scope.entries.toSet()
        val main = keys.issue(name, everything, expiresAt)
        webhookUrl?.let { endpoints.add(name, it, Subscription.EVENT_TYPES, main.principalId) }
        val restricted = keys.issue("$name$SECOND_PRINCIPAL", setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE), expiresAt)
        val revoked = keys.issue(name, everything, expiresAt).also { keys.revoke(it.keyId) }
        val expired = keys.issue(name, everything, now.minus(ALREADY_EXPIRED))
        val bounded = capabilities.describe().firstOrNull { it.limits.isKnown }?.id
        scripts.open(ConformanceRun(main.principalId, setOf(main.principalId, restricted.principalId), expiresAt))
        return ConformanceFixtures(
            apiKey = main.secret,
            expiresAt = expiresAt,
            inputs =
                listOfNotNull(
                    RESTRICTED_KEY to restricted.secret,
                    STATEMENTS_KEY to main.secret,
                    REVOKED_KEY to revoked.secret,
                    EXPIRED_KEY to expired.secret,
                    bounded?.let { BOUNDED_PROVIDER to it },
                ).toMap(),
            controlEndpoint = settings.controlEndpoint,
        )
    }

    override fun sweep() {
        val now = calendar.now().epochSecond
        subscriptions
            .all()
            .filter { it.managed && (expiryOf(it.name) ?: return@filter false) <= now }
            .forEach { endpoints.remove(it.name) }
        scripts.sweep()
    }

    @Synchronized
    private fun reserve(now: Instant): Instant {
        active.removeIf { !it.isAfter(now) }
        if (active.size >= settings.maxActive) {
            throw OpenFspException(ProblemType.RATE_LIMITED, "Every conformance preparation is in use.", retryAfter = Duration.between(now, active.min()))
        }
        return now.plus(settings.lifetime).also(active::add)
    }

    private fun expiryOf(name: String): Long? = name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore('-')?.toLongOrNull()

    private companion object {
        const val PREFIX = "conformance-"
        const val SECOND_PRINCIPAL = "-second"
        const val SUFFIX_BYTES = 6
        const val RESTRICTED_KEY = "restricted-key"
        const val STATEMENTS_KEY = "statements-key"
        const val REVOKED_KEY = "revoked-key"
        const val EXPIRED_KEY = "expired-key"
        const val BOUNDED_PROVIDER = "bounded-provider"
        val ALREADY_EXPIRED: Duration = Duration.ofDays(1)
    }
}

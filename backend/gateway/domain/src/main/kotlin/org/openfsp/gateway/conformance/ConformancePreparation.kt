package org.openfsp.gateway.conformance

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.account.api.ManageAccounts
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
    private val accounts: ManageAccounts,
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
        val account = accounts.open(name, expiresAt).id
        val main = keys.issue(account, PRIMARY, everything, expiresAt)
        webhookUrl?.let { endpoints.add(account, name, it, Subscription.EVENT_TYPES) }
        val restricted = keys.issue(account, SECOND, setOf(Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE), expiresAt)
        val revoked = keys.issue(account, PRIMARY, everything, expiresAt).also { keys.revoke(it.keyId) }
        val expired = keys.issue(account, PRIMARY, everything, now.minus(ALREADY_EXPIRED))
        val bounded = capabilities.describe(AccountFilter.Only(account)).firstOrNull { it.limits.isKnown }?.id
        scripts.open(ConformanceRun(account, main.principalId, expiresAt))
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
        val now = calendar.now()
        subscriptions
            .all()
            .filter { it.managed && accounts.find(it.account)?.isActiveAt(now) == false }
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

    private companion object {
        const val PREFIX = "conformance-"
        const val PRIMARY = "primary"
        const val SECOND = "second"
        const val SUFFIX_BYTES = 6
        const val RESTRICTED_KEY = "restricted-key"
        const val STATEMENTS_KEY = "statements-key"
        const val REVOKED_KEY = "revoked-key"
        const val EXPIRED_KEY = "expired-key"
        const val BOUNDED_PROVIDER = "bounded-provider"
        val ALREADY_EXPIRED: Duration = Duration.ofDays(1)
    }
}

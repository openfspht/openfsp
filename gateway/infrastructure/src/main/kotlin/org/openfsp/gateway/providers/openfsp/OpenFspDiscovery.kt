package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.PublicKeys
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant

class OpenFspCapabilities(
    private val providerId: String,
    private val api: OpenFspApi,
    private val mapper: OpenFspMapper,
    private val displayName: String?,
    private val logoUrl: URI?,
    private val clock: Clock,
    private val ttl: Duration = DEFAULT_TTL,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Cached(
        val descriptor: ProviderDescriptor,
        val until: Instant,
    )

    @Volatile
    private var cached: Cached? = null

    fun descriptor(): ProviderDescriptor {
        val current = cached
        if (current != null && clock.instant().isBefore(current.until)) return current.descriptor
        return synchronized(this) {
            val again = cached
            if (again != null && clock.instant().isBefore(again.until)) again.descriptor else refresh(again).descriptor
        }
    }

    private fun refresh(previous: Cached?): Cached {
        val now = clock.instant()
        val fresh =
            runCatching { api.capabilities().providers }
                .map { providers -> providers.firstOrNull { it.id == providerId } ?: providers.singleOrNull() ?: error("no provider announced") }
                .map { Cached(mapper.toDescriptor(it, displayName, logoUrl), now.plus(ttl)) }
                .onFailure { log.warn("Could not read the capabilities of {}: {}", providerId, it.message) }
                .getOrNull()
        val next = fresh ?: Cached(previous?.descriptor ?: mapper.unreachable(displayName, logoUrl), now.plus(RETRY_AFTER_FAILURE))
        cached = next
        return next
    }

    private companion object {
        val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
        val RETRY_AFTER_FAILURE: Duration = Duration.ofSeconds(30)
    }
}

class OpenFspWebhookKeys(
    private val providerId: String,
    private val api: OpenFspApi,
    private val clock: Clock,
) : PublicKeys {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var keys: Map<String, PublicKey> = emptyMap()

    @Volatile
    private var fetchedAt: Instant? = null

    override fun resolve(keyId: String): PublicKey? {
        val fetched = fetchedAt
        if (fetched == null || clock.instant().isAfter(fetched.plus(MAX_AGE))) refresh()
        return keys[keyId] ?: refresh().let { keys[keyId] }
    }

    @Synchronized
    private fun refresh() {
        val now = clock.instant()
        val last = fetchedAt
        if (last != null && now.isBefore(last.plus(MIN_INTERVAL))) return
        fetchedAt = now
        runCatching { api.configuration().webhookKeys }
            .onSuccess { published ->
                keys = published.filter { KeyStatus.fromWire(it.openfspStatus) != null }.mapNotNull { jwk -> jwk.publicKeyOrNull()?.let { jwk.kid to it } }.toMap()
            }.onFailure { log.warn("Could not read the webhook keys of {}: {}", providerId, it.message) }
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofSeconds(10)
        val MAX_AGE: Duration = Duration.ofHours(1)
    }
}

package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.sdk.OpenFsp
import org.openfsp.sdk.OpenFspException
import org.openfsp.sdk.model.Provider
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

class OpenFspCapabilities(
    private val providerId: String,
    private val server: OpenFsp,
    private val mapper: OpenFspMapper,
    private val displayName: String?,
    private val logoUrl: URI?,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Described(
        val announced: Provider,
        val descriptor: ProviderDescriptor,
    )

    @Volatile
    private var described: Described? = null

    @Volatile
    private var silentUntil: Instant? = null

    fun descriptor(): ProviderDescriptor {
        val fallback = described?.descriptor ?: mapper.unreachable(displayName, logoUrl)
        if (silentUntil?.let { clock.instant().isBefore(it) } == true) return fallback
        return try {
            val providers = server.providers()
            val announced = providers.firstOrNull { it.id == providerId } ?: providers.singleOrNull() ?: error("no provider announced")
            described?.takeIf { it.announced === announced }?.descriptor
                ?: mapper.toDescriptor(announced, displayName, logoUrl).also { described = Described(announced, it) }
        } catch (failure: OpenFspException) {
            unread(failure, fallback)
        } catch (failure: IllegalStateException) {
            unread(failure, fallback)
        }
    }

    private fun unread(
        failure: RuntimeException,
        fallback: ProviderDescriptor,
    ): ProviderDescriptor {
        log.warn("Could not read the capabilities of {}: {}", providerId, failure.message)
        silentUntil = clock.instant().plus(RETRY_AFTER_FAILURE)
        return fallback
    }

    private companion object {
        val RETRY_AFTER_FAILURE: Duration = Duration.ofSeconds(30)
    }
}

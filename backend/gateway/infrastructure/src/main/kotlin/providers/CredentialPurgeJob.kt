package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.api.ManageProviderCredentials
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CredentialPurgeJob(
    private val credentials: ManageProviderCredentials,
    private val providers: ConfiguredProviders,
) {
    @Scheduled(fixedDelayString = "\${openfsp.credential-purge-interval:15m}", initialDelayString = "\${openfsp.credential-purge-interval:15m}")
    fun run() {
        credentials.purgeRetired(BATCH, MAX_BATCHES_PER_RUN)
        providers.evictUnusable()
    }

    private companion object {
        const val BATCH = 100
        const val MAX_BATCHES_PER_RUN = 10
    }
}

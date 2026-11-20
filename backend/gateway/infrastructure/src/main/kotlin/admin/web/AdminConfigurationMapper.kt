package org.openfsp.gateway.admin.web

import org.openfsp.gateway.webhook.PublishedSigningKey
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class AdminConfigurationMapper {
    fun toResource(
        configuration: ConfigurationResource,
        capabilities: CapabilitiesResource,
        keys: List<PublishedSigningKey>,
        rotateEvery: Duration?,
        nextRotationAt: Instant?,
    ): AdminConfigurationResource =
        AdminConfigurationResource(
            configuration = configuration,
            capabilities = capabilities,
            signing =
                SigningResource(
                    keys = keys.map { SigningKeyState(it.kid, it.status.wire, it.activatesAt, it.retiredAt) },
                    rotateEveryMinutes = rotateEvery?.toMinutes()?.takeIf { it > 0 },
                    nextRotationAt = nextRotationAt,
                ),
        )
}

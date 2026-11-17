package org.openfsp.gateway.admin.configuration

import org.openfsp.gateway.capabilities.Capabilities
import org.openfsp.gateway.capabilities.CapabilitiesMapper
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.discovery.ServiceConfiguration
import org.openfsp.gateway.discovery.ServiceConfigurationReader
import org.openfsp.gateway.webhook.KeyRotationPolicy
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class SigningKeyState(
    val kid: String,
    val status: String,
    val activatesAt: Instant,
    val retiredAt: Instant?,
)

data class SigningResource(
    val keys: List<SigningKeyState>,
    val rotateEveryMinutes: Long?,
    val nextRotationAt: Instant?,
)

data class AdminConfigurationResource(
    val configuration: ServiceConfiguration,
    val capabilities: Capabilities,
    val signing: SigningResource,
)

@RestController
@RequestMapping("/admin/configuration")
class AdminConfigurationController(
    private val reader: ServiceConfigurationReader,
    private val capabilities: DescribeCapabilities,
    private val capabilitiesMapper: CapabilitiesMapper,
    private val rotation: RotateSigningKeys,
    private val policy: KeyRotationPolicy,
) {
    @GetMapping
    fun current(): AdminConfigurationResource {
        val configuration = reader.read()
        return AdminConfigurationResource(
            configuration = configuration,
            capabilities = capabilitiesMapper.toResource(capabilities.describe()),
            signing =
                SigningResource(
                    keys = rotation.published().map { SigningKeyState(it.kid, it.status.wire, it.activatesAt, it.retiredAt) },
                    rotateEveryMinutes = policy.rotateEvery?.toMinutes()?.takeIf { it > 0 },
                    nextRotationAt = rotation.nextRotationAt(),
                ),
        )
    }
}

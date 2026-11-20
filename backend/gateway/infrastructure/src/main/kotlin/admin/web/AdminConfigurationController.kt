package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.capabilities.CapabilitiesMapper
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.protocol.api.ConfigurationResource
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
    val configuration: ConfigurationResource,
    val capabilities: CapabilitiesResource,
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
    private val mapper: AdminConfigurationMapper,
) {
    @GetMapping
    fun current(): AdminConfigurationResource =
        mapper.toResource(
            reader.read(),
            capabilitiesMapper.toResource(capabilities.describe(AccountFilter.Every)),
            rotation.published(),
            policy.rotateEvery,
            rotation.nextRotationAt(),
        )
}

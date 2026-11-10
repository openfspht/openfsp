package org.openfsp.gateway.admin.configuration

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.controllers.capabilities.Capabilities
import org.openfsp.gateway.controllers.capabilities.CapabilitiesMapper
import org.openfsp.gateway.controllers.discovery.ServiceConfiguration
import org.openfsp.gateway.controllers.discovery.ServiceConfigurationMapper
import org.openfsp.gateway.controllers.webhooks.JwkMapper
import org.openfsp.gateway.controllers.webhooks.JwkSetResource
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ConfigurationResource(
    val configuration: ServiceConfiguration,
    val capabilities: Capabilities,
    val webhookKeys: JwkSetResource,
)

@RestController
@RequestMapping("/admin/configuration")
class AdminConfigurationController(
    private val capabilities: DescribeCapabilities,
    private val configurationMapper: ServiceConfigurationMapper,
    private val capabilitiesMapper: CapabilitiesMapper,
    private val properties: GatewayProperties,
    private val rotation: RotateSigningKeys,
    private val jwks: JwkMapper,
) {
    @GetMapping
    fun current(): ConfigurationResource =
        ConfigurationResource(
            configuration = configurationMapper.toResource(properties.publicBaseUrl, capabilities.supported()),
            capabilities = capabilitiesMapper.toResource(capabilities.describe()),
            webhookKeys = JwkSetResource(rotation.current().let { rotation.published().map(jwks::toResource) }),
        )
}

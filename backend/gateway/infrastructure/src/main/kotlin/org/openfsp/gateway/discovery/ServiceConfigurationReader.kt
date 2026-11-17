package org.openfsp.gateway.discovery

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.stereotype.Component

@Component
class ServiceConfigurationReader(
    private val capabilities: DescribeCapabilities,
    private val rotation: RotateSigningKeys,
    private val jwks: JwkMapper,
    private val mapper: ServiceConfigurationMapper,
    private val properties: GatewayProperties,
) {
    fun read(): ServiceConfiguration = mapper.toResource(properties.publicBaseUrl, capabilities.supported(), rotation.published().map(jwks::toResource))
}

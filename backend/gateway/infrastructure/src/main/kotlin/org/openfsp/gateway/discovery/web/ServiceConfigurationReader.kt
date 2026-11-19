package org.openfsp.gateway.discovery.web

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.conformance.ConformanceProperties
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.protocol.api.ConfigurationResource
import org.springframework.stereotype.Component

@Component
class ServiceConfigurationReader(
    private val capabilities: DescribeCapabilities,
    private val rotation: RotateSigningKeys,
    private val jwks: JwkMapper,
    private val mapper: ServiceConfigurationMapper,
    private val properties: GatewayProperties,
    private val conformance: ConformanceProperties,
) {
    fun read(): ConfigurationResource = mapper.toResource(properties.publicBaseUrl, capabilities.supported(), rotation.published().map(jwks::toResource), conformance.enabled)
}

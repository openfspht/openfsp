package org.openfsp.gateway.controllers.discovery

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@Component
class ServiceConfigurationReader(
    private val capabilities: DescribeCapabilities,
    private val rotation: RotateSigningKeys,
    private val jwks: JwkMapper,
    private val mapper: ServiceConfigurationMapper,
    private val properties: GatewayProperties,
) {
    fun read(): ServiceConfiguration {
        rotation.current()
        return mapper.toResource(properties.publicBaseUrl, capabilities.supported(), rotation.published().map(jwks::toResource))
    }
}

@RestController
class DiscoveryController(
    private val reader: ServiceConfigurationReader,
) {
    @GetMapping("/.well-known/openfsp-configuration")
    fun configuration(): ResponseEntity<ServiceConfiguration> = ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(reader.read())

    private companion object {
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}

package org.openfsp.gateway.controllers.discovery

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.configuration.GatewayProperties
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
class DiscoveryController(
    private val capabilities: DescribeCapabilities,
    private val mapper: ServiceConfigurationMapper,
    private val properties: GatewayProperties,
) {
    @GetMapping("/.well-known/openfsp-configuration")
    fun configuration(): ResponseEntity<ServiceConfiguration> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE))
            .body(mapper.toResource(properties.publicBaseUrl, capabilities.supported()))

    private companion object {
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}

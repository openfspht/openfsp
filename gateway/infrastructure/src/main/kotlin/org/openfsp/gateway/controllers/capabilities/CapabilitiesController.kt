package org.openfsp.gateway.controllers.capabilities

import org.openfsp.gateway.auth.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
class CapabilitiesController(
    private val capabilities: DescribeCapabilities,
    private val mapper: CapabilitiesMapper,
) {
    @RequiresScope(Scope.CAPABILITIES_READ)
    @GetMapping("/v1/capabilities")
    fun capabilities(): ResponseEntity<Capabilities> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE))
            .body(mapper.toResource(capabilities.describe()))

    private companion object {
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}

package org.openfsp.gateway.discovery

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

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

package org.openfsp.gateway.capabilities

import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.apikeys.RequiresScope
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
    fun capabilities(caller: AuthenticatedKey): ResponseEntity<CapabilitiesResource> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE).cachePrivate())
            .body(mapper.toResource(capabilities.describe(AccountFilter.Only(caller.accountId))))

    private companion object {
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}

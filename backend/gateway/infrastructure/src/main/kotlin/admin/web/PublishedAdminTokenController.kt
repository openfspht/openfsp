package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.DemoMode
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class PublishedAdminTokenResource(
    val token: String,
)

@RestController
class PublishedAdminTokenController(
    demo: DemoMode,
    properties: GatewayProperties,
    hashing: AdminTokenHashing,
) {
    private val published =
        AdminTokenHashing.DEMO_TOKEN.takeIf { token ->
            demo.active && properties.adminToken?.let { hashing.matches(token, it) } == true
        }

    @GetMapping(AdminAccessFilter.PUBLISHED_TOKEN)
    fun publishedToken(): PublishedAdminTokenResource = PublishedAdminTokenResource(published ?: throw OpenFspException.notFound())
}

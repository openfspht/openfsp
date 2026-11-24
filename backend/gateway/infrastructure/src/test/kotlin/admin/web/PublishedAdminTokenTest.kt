package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.DemoMode
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.security.AdminTokenHashing
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublishedAdminTokenTest {
    private val hashing = AdminTokenHashing()

    private fun controller(
        demo: Boolean,
        adminToken: String?,
    ) = PublishedAdminTokenController(
        DemoMode(demo),
        GatewayProperties(
            environment = GatewayProperties.Environment.TEST,
            developmentMode = false,
            publicBaseUrl = URI("https://gateway.example"),
            appKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 3 }),
            adminToken = adminToken,
        ),
        hashing,
    )

    @Test
    fun `the demo publishes its admin token`() {
        assertEquals(AdminTokenHashing.DEMO_TOKEN, controller(demo = true, adminToken = hashing.hash(AdminTokenHashing.DEMO_TOKEN)).publishedToken().token)
    }

    @Test
    fun `a token of the operator's own is never published, even in the demo`() {
        assertFailsWith<OpenFspException> { controller(demo = true, adminToken = hashing.hash("operator token")).publishedToken() }
        assertFailsWith<OpenFspException> { controller(demo = true, adminToken = null).publishedToken() }
    }

    @Test
    fun `outside the demo no token is published`() {
        assertFailsWith<OpenFspException> { controller(demo = false, adminToken = hashing.hash(AdminTokenHashing.DEMO_TOKEN)).publishedToken() }
    }
}

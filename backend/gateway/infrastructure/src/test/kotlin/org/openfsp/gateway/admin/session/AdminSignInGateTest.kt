package org.openfsp.gateway.admin.session

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminSignInGateTest {
    private val gate =
        AdminSignInGate(
            GatewayProperties(
                environment = GatewayProperties.Environment.TEST,
                developmentMode = false,
                publicBaseUrl = URI("https://gateway.example"),
                appKey = Base64.getEncoder().encodeToString(ByteArray(32)),
                adminSignInConcurrency = 1,
            ),
        )

    @Test
    fun `a sign-in beyond the concurrent verifications is refused before hashing, and the slot is freed afterwards`() {
        val refused = gate.verify { assertFailsWith<OpenFspException> { gate.verify { error("never hashed") } } }
        assertEquals(ProblemType.RATE_LIMITED, refused.type)
        assertEquals(true, gate.verify { true })
        assertFailsWith<IllegalStateException> { gate.verify { error("failure") } }
        assertEquals(true, gate.verify { true })
    }
}

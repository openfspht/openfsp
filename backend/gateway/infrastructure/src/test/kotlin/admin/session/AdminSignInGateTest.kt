package org.openfsp.gateway.admin.session

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminSignInGateTest {
    private val gate = AdminSignInGate()

    @Test
    fun `a sign-in beyond the concurrent verifications is refused before hashing, and the slot is freed afterwards`() {
        val refused = gate.verify { gate.verify { assertFailsWith<OpenFspException> { gate.verify { error("never hashed") } } } }
        assertEquals(ProblemType.RATE_LIMITED, refused.type)
        assertEquals(true, gate.verify { true })
        assertFailsWith<IllegalStateException> { gate.verify { error("failure") } }
        assertEquals(true, gate.verify { gate.verify { true } })
    }
}

package org.openfsp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentifiersTest {
    @Test
    fun `resource ids carry the prefix and fit the ResourceId syntax`() {
        val id = ResourceIds.generate("pay")
        assertTrue(id.startsWith("pay_"))
        assertEquals(4 + 26, id.length)
        assertTrue(ResourceIds.isValid(id))
    }

    @Test
    fun `resource ids are unique and sort in creation order`() {
        val ids = List(1_000) { ResourceIds.generate("pay") }
        assertEquals(1_000, ids.toSet().size)
        assertEquals(ids, ids.sorted())
    }

    @Test
    fun `idempotency keys follow their syntax`() {
        assertTrue(IdempotencyKeys.isValid("0b9c2b6e-6f1d-4c55-9a43-2f1b7d1f3a10"))
        assertTrue(!IdempotencyKeys.isValid(""))
    }
}

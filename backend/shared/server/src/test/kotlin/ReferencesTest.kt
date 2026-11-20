package org.openfsp.server

import kotlin.test.Test
import kotlin.test.assertTrue

class ReferencesTest {
    @Test
    fun `references follow their syntax`() {
        assertTrue(References.isValid("INV-2026-00184"))
        assertTrue(!References.isValid("INV 184"))
    }
}

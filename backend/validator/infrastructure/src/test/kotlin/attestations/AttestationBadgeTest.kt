package org.openfsp.validator.attestations

import org.openfsp.validator.engine.LevelVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttestationBadgeTest {
    private val badge = AttestationBadge()

    @Test
    fun `an incomplete Core reads incomplete, not fail`() {
        assertEquals("pass", badge.verdictText(LevelVerdict.PASS))
        assertEquals("incomplete", badge.verdictText(LevelVerdict.INCOMPLETE))
        assertEquals("fail", badge.verdictText(LevelVerdict.FAIL))
        assertTrue(badge.svg(LevelVerdict.INCOMPLETE, "0.1.0", "https://validator.test/attestations/1").contains("incomplete, protocol 0.1.0"))
    }
}

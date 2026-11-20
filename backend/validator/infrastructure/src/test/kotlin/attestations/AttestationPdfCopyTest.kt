package org.openfsp.validator.attestations

import org.openfsp.validator.attestation.Attestation
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals

class AttestationPdfCopyTest {
    private fun bundle(locale: Locale) = ResourceBundle.getBundle("attestations/pdf", locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))

    @Test
    fun `the English copy states what the signed attestation states`() {
        assertEquals(Attestation.STATEMENT, bundle(Locale.ROOT).getString("statement"))
        assertEquals(Attestation.VALIDITY, bundle(Locale.ROOT).getString("validityStatement"))
    }

    @Test
    fun `both languages carry the same keys`() {
        assertEquals(bundle(Locale.ROOT).keySet(), bundle(Locale.FRENCH).keySet())
    }
}

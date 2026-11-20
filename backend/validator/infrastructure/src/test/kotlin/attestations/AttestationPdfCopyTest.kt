package org.openfsp.validator.attestations

import org.openfsp.validator.attestation.Attestation
import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties
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

    private fun keysOf(file: String): Set<String> = Properties().apply { AttestationPdfCopyTest::class.java.getResourceAsStream("/attestations/$file")!!.use { load(InputStreamReader(it, Charsets.UTF_8)) } }.stringPropertyNames()

    @Test
    fun `both translation files carry the same keys`() {
        assertEquals(keysOf("pdf.properties"), keysOf("pdf_fr.properties"))
    }
}

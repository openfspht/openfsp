package org.openfsp.validator.attestations

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.attestation.AttestedReport
import org.openfsp.validator.attestation.AttestedRun
import org.openfsp.validator.attestation.AttestedTarget
import org.openfsp.validator.attestation.AttestedVerdicts
import org.openfsp.validator.attestation.Validity
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.report.Claim
import org.openfsp.validator.report.ProfileResult
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.suite.Limit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AttestationPdfTest {
    private val pdf = AttestationPdf()
    private val id = "d637d955-30c1-4ca0-a5a6-16d8b41214de"
    private val verifyUrl = "https://validator.openfsp.org/attestations/$id"

    private fun attestation(
        profiles: Int,
        inputs: Int,
        limits: List<Limit>,
    ) = Attestation(
        type = Attestation.TYPE,
        id = id,
        issuer = "https://validator.openfsp.org",
        issuedAt = "2026-11-21T22:10:28.342802834Z",
        statement = Attestation.STATEMENT,
        claim = Claim("server", "none", emptyList(), "0.1.0"),
        suite = SuiteVersion("0.1.0", "0.1.0"),
        target = AttestedTarget("server", "https://gateway.openfsp.org", "0.1.0"),
        run = AttestedRun("354d1bce-ac9d-40ba-8d61-556c87a95bf8", "2026-11-21T22:04:06.958948693Z", 284223),
        configuration =
            mapOf(
                "api_key" to "[REDACTED]",
                "base_url" to "https://gateway.openfsp.org/a/rather/long/path/that/keeps/going/for/a/while",
                "conformance" to "openfsp conformance suite 0.1.0",
                "inputs" to (1..inputs).associate { "input-number-$it" to "a long input value for the scenario number $it" },
                "outcomes" to "suite",
                "prepared_by" to "server",
                "provider" to "moncash",
                "only" to "API-5.2.1",
                "target" to "server",
                "timeout" to "PT30S",
                "webhook_url" to "https://validator.openfsp.org/api/runs/354d1bce-ac9d-40ba-8d61-556c87a95bf8/webhooks/with/an/even/longer/tail",
            ),
        verdicts =
            AttestedVerdicts(
                LevelVerdict.INCOMPLETE,
                (1..profiles).map { ProfileResult("profile.number_$it", LevelVerdict.entries[it % LevelVerdict.entries.size]) },
                mapOf(Verdict.PASS to 58, Verdict.INCONCLUSIVE to 1, Verdict.FAIL to 2),
            ),
        report = AttestedReport("354d1bce-ac9d-40ba-8d61-556c87a95bf8", Attestation.DIGEST_ALGORITHM, Attestation.CANONICALIZATION, "5c08377c735f747e1085e8e6762088dd02d34d50bd19b2ffd5d40df2a7b9c184"),
        limits = limits,
        validity = Validity(Attestation.VALIDITY, Attestation.VALIDITY_REFERENCE),
    )

    private val suiteLimits =
        listOf(
            Limit("conformance#8.1", "A conformance report is not a security audit."),
            Limit("conformance#6.3", "A result obtained against the mock server holds only for the configuration that produced it and says nothing of a real provider."),
        )

    private fun text(bytes: ByteArray): String = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }

    private fun pages(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    @Test
    fun `a large attestation fits on one page in both languages`() {
        val many = suiteLimits + (1..4).map { Limit("conformance#9.$it", "An additional limit that takes a fair amount of room on the page, number $it.") }
        PdfLocale.entries.forEach { locale ->
            assertEquals(1, pages(pdf.render(attestation(profiles = 12, inputs = 8, limits = many), "bYlVIzwq-VRoyrGOVYj9FiivdFc7OXQ1OXau57oIGS4", verifyUrl, locale)))
        }
    }

    @Test
    fun `an extreme attestation still renders a single page`() {
        PdfLocale.entries.forEach { locale ->
            assertEquals(1, pages(pdf.render(attestation(profiles = 60, inputs = 40, limits = suiteLimits), "kid", verifyUrl, locale)))
        }
    }

    @Test
    fun `the French attestation is written in French`() {
        val text = text(pdf.render(attestation(profiles = 5, inputs = 5, limits = suiteLimits), "kid", verifyUrl, PdfLocale.FR))
        assertContains(text, "Un rapport de conformité n'est pas un audit de sécurité.")
        assertContains(text, "21 novembre 2026, 22:10 UTC")
        assertContains(text, "serveur")
        assertContains(text, "58 scénarios Core réussis sur 61")
        assertContains(text, "1 non concluant")
        assertContains(text, "2 échoués")
        assertFalse("A conformance report is not a security audit." in text)
        assertContains(text, "au moins un scénario n'a pas pu être vérifié")
        assertFalse('?' in text)
    }

    @Test
    fun `the English attestation formats dates and the Core tally`() {
        val text = text(pdf.render(attestation(profiles = 5, inputs = 5, limits = suiteLimits), "kid", verifyUrl, PdfLocale.EN))
        assertContains(text, "21 November 2026, 22:10 UTC")
        assertContains(text, "58 of 61 Core scenarios passed")
        assertContains(text, "A conformance report is not a security audit.")
    }

    @Test
    fun `an unknown limit falls back to its signed text`() {
        val text = text(pdf.render(attestation(profiles = 1, inputs = 1, limits = listOf(Limit("conformance#99.1", "Signed English limit."))), "kid", verifyUrl, PdfLocale.FR))
        assertContains(text, "Signed English limit.")
    }
}

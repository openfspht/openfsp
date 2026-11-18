package org.openfsp.validator.attestation

import org.openfsp.validator.TestSuites
import org.openfsp.validator.attestation.spi.stubs.DottedAttestationSignatures
import org.openfsp.validator.attestation.spi.stubs.GeneratedSigningKeys
import org.openfsp.validator.attestation.spi.stubs.InMemoryAttestationStore
import org.openfsp.validator.attestation.spi.stubs.RememberedAttestationFormat
import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.spi.stubs.KnownSchemaCheckers
import org.openfsp.validator.engine.spi.stubs.RecordingSimulatorControl
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.StoredReport
import org.openfsp.validator.report.spi.stubs.InMemoryReportStore
import org.openfsp.validator.report.spi.stubs.RememberedReportFormat
import org.openfsp.validator.run.PreparedRuns
import org.openfsp.validator.run.RunSessions
import org.openfsp.validator.run.RunStateQuery
import org.openfsp.validator.run.TestSettings
import org.openfsp.validator.suite.TargetKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssueAttestationsTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC)
    private val reports = InMemoryReportStore()
    private val format = RememberedReportFormat()
    private val store = InMemoryAttestationStore()
    private val settings = TestSettings.run()
    private val sessions = RunSessions(settings, clock)
    private val prepared = PreparedRuns(settings, sessions, clock)

    private fun issuer(keys: GeneratedSigningKeys = GeneratedSigningKeys()) =
        AttestationIssuer(store, reports, format, keys, DottedAttestationSignatures(), RememberedAttestationFormat(), PlainJsonCodec, RunStateQuery(prepared, sessions), AttestationSettings("https://validator.test/"), clock)

    private fun storedRun(): UUID {
        val suite = TestSuites.things()
        val report = ConformanceRunner(RecordingSimulatorControl(), KnownSchemaCheckers(), PlainJsonCodec).run(suite, Configuration(TargetKind.SERVER, "http://target.test", "ofsp_test_key", null), TestSuites.target())
        val id = UUID.randomUUID()
        reports.save(StoredReport(id, clock.instant(), "server", "http://target.test", report.core.name, report.suite.version, format.write(report)))
        return id
    }

    @Test
    fun `an attestation names its report by digest, is issued once and verifies`() {
        val issuer = issuer()
        val run = storedRun()

        val issued = issuer.issue(run)
        assertTrue(issued.created)
        assertEquals(Attestation.TYPE, issued.document.type)
        assertEquals("https://validator.test", issued.document.issuer)
        assertEquals(format.digest(reports.find(run)!!.body), issued.document.report.digest)
        assertEquals("https://validator.test/attestations/${issued.stored.id}", issuer.pageUrl(issued.stored.id))

        val again = issuer.issue(run)
        assertFalse(again.created)
        assertEquals(issued.stored.id, again.stored.id)
        assertEquals(issued.stored.id, issuer.forRun(run).stored.id)

        val verification = issuer.verify(issued.stored.id)
        assertTrue(verification.valid, verification.problems.toString())
    }

    @Test
    fun `a report changed after issue no longer matches its attestation`() {
        val issuer = issuer()
        val run = storedRun()
        val issued = issuer.issue(run)
        val stored = reports.find(run)!!
        reports.save(stored.copy(body = stored.body.replace("core=INCOMPLETE", "core=PASS")))

        val verification = issuer.verify(issued.stored.id)
        assertFalse(verification.valid)
        assertTrue(verification.signatureValid)
        assertFalse(verification.digestMatches)
    }

    @Test
    fun `no key, no report or an unstarted run refuse the attestation`() {
        assertEquals(ProblemKind.ATTESTATION_UNAVAILABLE, assertFailsWith<ValidatorException> { issuer(GeneratedSigningKeys(enabled = false)).issue(storedRun()) }.kind)
        assertEquals(ProblemKind.NOT_FOUND, assertFailsWith<ValidatorException> { issuer().issue(UUID.randomUUID()) }.kind)
        val waiting = prepared.prepare(TargetKind.SERVER)
        assertEquals(ProblemKind.RUN_NOT_STARTED, assertFailsWith<ValidatorException> { issuer().issue(waiting.id) }.kind)
    }
}

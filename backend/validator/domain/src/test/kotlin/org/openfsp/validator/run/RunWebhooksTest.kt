package org.openfsp.validator.run

import org.openfsp.validator.engine.Pointers
import org.openfsp.validator.engine.WebhookRecorder
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.api.RunState
import org.openfsp.validator.suite.TargetKind
import java.time.Clock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RunWebhooksTest {
    private val settings = TestSettings.run(webhookBytes = 10, maxPreparedRuns = 2)
    private val sessions = RunSessions(settings, Clock.systemUTC())

    @Test
    fun `the receiver of a run accepts deliveries only while the run is running`() {
        var state = RunState.PREPARED
        val webhooks = RunWebhooks(settings, sessions, { state }, PlainJsonCodec, Pointers())
        val id = UUID.randomUUID()
        webhooks.open(id)

        assertNull(webhooks.receive(id, "POST", "", emptyMap(), "{}".toByteArray()))
        state = RunState.RUNNING
        assertNotNull(webhooks.receive(id, "POST", "", emptyMap(), "{}".toByteArray()))
        state = RunState.ENDED
        assertNull(webhooks.receive(id, "POST", "", emptyMap(), "{}".toByteArray()))
    }

    @Test
    fun `a delivery beyond the byte budget of the run is refused and counted, never silently dropped`() {
        val recorder = WebhookRecorder("http://validator.test/hooks", PlainJsonCodec, Pointers(), capacity = 10, budget = 10)

        assertEquals(204, recorder.receive("POST", recorder.url, emptyMap(), "{\"a\":1}".toByteArray()).status)
        assertEquals(503, recorder.receive("POST", recorder.url, emptyMap(), "{\"a\":2}".toByteArray()).status)

        assertEquals(1, recorder.deliveries().size)
        assertEquals(1, recorder.refused())
    }

    @Test
    fun `the validator holds a bounded number of prepared runs`() {
        val prepared = PreparedRuns(settings, sessions, Clock.systemUTC())
        repeat(2) { prepared.prepare(TargetKind.SERVER) }

        assertEquals(ProblemKind.BUSY, assertFailsWith<ValidatorException> { prepared.prepare(TargetKind.SERVER) }.kind)
    }
}

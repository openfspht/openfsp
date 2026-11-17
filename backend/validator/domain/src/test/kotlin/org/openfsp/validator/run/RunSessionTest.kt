package org.openfsp.validator.run

import org.openfsp.validator.run.spi.RunEventStream
import org.openfsp.validator.run.spi.stubs.CollectingRunEventStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunSessionTest {
    private class StalledStream : RunEventStream {
        val release = CountDownLatch(1)
        val names = CopyOnWriteArrayList<String>()

        @Volatile
        var completed = false

        override fun send(events: List<RunEvent>): Boolean {
            if (events.isEmpty()) return true
            release.await()
            names += events.map { it.name }
            return true
        }

        override fun complete() {
            completed = true
        }

        override fun onClose(action: () -> Unit) {}
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "the condition did not hold within 5 seconds" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a stalled follower holds neither the publisher nor the other followers`() {
        val session = RunSession(UUID.randomUUID())
        val stalled = StalledStream()
        val other = CollectingRunEventStream()
        session.subscribe(null, stalled)
        session.subscribe(null, other)

        val published =
            CompletableFuture.runAsync {
                session.publish(RunEvents.RUN, "plan")
                session.publish(RunEvents.SUMMARY, "summary")
                session.finish(Instant.now())
            }
        published.get(2, TimeUnit.SECONDS)
        eventually { other.completed }
        assertEquals(listOf(RunEvents.RUN, RunEvents.SUMMARY), other.events.map { it.name })

        stalled.release.countDown()
        eventually { stalled.completed }
        assertEquals(listOf(RunEvents.RUN, RunEvents.SUMMARY), stalled.names)
    }

    @Test
    fun `a follower that joins late receives the events after its last one, in order`() {
        val session = RunSession(UUID.randomUUID())
        repeat(3) { session.publish(RunEvents.SCENARIO_FINISHED, it) }
        val late = CollectingRunEventStream()
        session.subscribe(1, late)
        session.publish(RunEvents.SUMMARY, "summary")
        session.finish(Instant.now())
        eventually { late.completed }
        assertEquals(listOf(2L, 3L, 4L), late.events.map { it.id })
        assertTrue(late.completed)
    }
}

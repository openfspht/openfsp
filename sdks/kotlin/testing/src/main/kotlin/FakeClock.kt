package org.openfsp.sdk.testing

import org.openfsp.sdk.http.OpenFspClock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class FakeClock
    @JvmOverloads
    constructor(
        start: Instant = Instant.parse("2026-11-15T12:00:00Z"),
    ) : OpenFspClock {
        private val current = AtomicReference(start)

        val sleeps: MutableList<Duration> = CopyOnWriteArrayList()

        override fun now(): Instant = current.get()

        override fun sleep(duration: Duration) {
            sleeps += duration
            advance(duration)
        }

        override fun random(): Double = 1.0

        fun advance(duration: Duration) {
            current.updateAndGet { it.plus(duration) }
        }
    }

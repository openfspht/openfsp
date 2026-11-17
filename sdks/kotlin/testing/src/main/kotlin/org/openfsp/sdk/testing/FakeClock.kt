package org.openfsp.sdk.testing

import org.openfsp.sdk.http.OpenFspClock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class FakeClock
    @JvmOverloads
    constructor(
        @Volatile private var current: Instant = Instant.parse("2026-11-15T12:00:00Z"),
    ) : OpenFspClock {
        val sleeps: MutableList<Duration> = CopyOnWriteArrayList()

        override fun now(): Instant = current

        override fun sleep(duration: Duration) {
            sleeps += duration
            current = current.plus(duration)
        }

        override fun random(): Double = 1.0

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

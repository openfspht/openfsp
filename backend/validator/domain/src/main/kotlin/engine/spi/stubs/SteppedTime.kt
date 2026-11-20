package org.openfsp.validator.engine.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.spi.Pause
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class SteppedTime(
    start: Instant = Instant.parse("2026-11-18T12:00:00Z"),
) : Clock(),
    Pause {
    @Volatile
    private var now: Instant = start
    val pauses = CopyOnWriteArrayList<Duration>()

    override fun pause(duration: Duration) {
        pauses += duration
        now = now.plus(duration)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

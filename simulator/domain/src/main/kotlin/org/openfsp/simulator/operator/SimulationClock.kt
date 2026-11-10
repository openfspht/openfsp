package org.openfsp.simulator.operator

import org.openfsp.ddd.DomainService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

@DomainService
class SimulationClock(
    private val clock: Clock,
) {
    private val offset = AtomicLong()

    fun now(): Instant = clock.instant().plusSeconds(offset.get())

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), HAITI)

    fun localStamp(): String = LOCAL.format(localNow())

    fun advance(by: Duration) {
        offset.addAndGet(by.seconds)
    }

    fun reset() = offset.set(0)

    private companion object {
        val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")
        val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}

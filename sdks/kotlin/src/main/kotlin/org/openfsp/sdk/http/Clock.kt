package org.openfsp.sdk.http

import org.openfsp.sdk.TransportException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

interface Clock {
    fun now(): Instant

    fun sleep(duration: Duration)

    fun random(): Double

    companion object {
        @JvmField
        val SYSTEM: Clock =
            object : Clock {
                override fun now(): Instant = Instant.now()

                override fun sleep(duration: Duration) {
                    try {
                        Thread.sleep(duration.toMillis())
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw TransportException("interrupted while waiting to retry", interrupted)
                    }
                }

                override fun random(): Double = Random.nextDouble()
            }
    }
}

internal fun Clock.asJavaClock(): java.time.Clock =
    object : java.time.Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): java.time.Clock = this

        override fun instant(): Instant = now()
    }

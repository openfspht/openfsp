package org.openfsp.gateway.conformance.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.conformance.spi.Deferrals
import java.time.Duration

@Stub
class ManualDeferrals : Deferrals {
    private class Deferred(
        val delay: Duration,
        val action: () -> Unit,
    )

    private val pending = mutableListOf<Deferred>()

    val delays: List<Duration> get() = pending.map { it.delay }

    override fun after(
        delay: Duration,
        action: () -> Unit,
    ) {
        pending += Deferred(delay, action)
    }

    fun runDue(within: Duration = Duration.ZERO) {
        while (true) {
            val due = pending.firstOrNull { it.delay <= within } ?: return
            pending -= due
            due.action()
        }
    }
}

package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class RunSlot(
    private val release: () -> Unit,
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) release()
    }
}

@DomainService
class RunSlots(
    settings: RunSettings,
) {
    val capacity: Int = settings.concurrency.coerceAtLeast(1)

    private val permits = Semaphore(capacity)

    fun acquire(): RunSlot? = if (permits.tryAcquire()) RunSlot(permits::release) else null
}

package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.stereotype.Component
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

interface RunSlots {
    val capacity: Int

    fun acquire(): RunSlot?
}

@Component
class RunPool(
    properties: ValidatorProperties,
) : RunSlots {
    override val capacity: Int = properties.concurrency.coerceAtLeast(1)

    private val permits = Semaphore(capacity)

    override fun acquire(): RunSlot? = if (permits.tryAcquire()) RunSlot(permits::release) else null
}

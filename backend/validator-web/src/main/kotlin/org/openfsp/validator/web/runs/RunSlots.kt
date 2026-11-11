package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

class RunSlot(
    val simulatorControlUrl: String,
    private val release: () -> Unit,
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) release()
    }
}

interface RunSlots {
    fun acquire(): RunSlot?
}

@Component
class SingleRunSlot(
    private val properties: ValidatorProperties,
) : RunSlots {
    private val busy = AtomicBoolean(false)

    override fun acquire(): RunSlot? {
        if (!busy.compareAndSet(false, true)) return null
        return RunSlot(properties.simulator.controlUrl) { busy.set(false) }
    }
}

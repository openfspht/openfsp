package org.openfsp.validator.run.spi

import org.openfsp.validator.run.RunEvent

interface RunEventStream {
    fun send(events: List<RunEvent>): Boolean

    fun complete()

    fun onClose(action: () -> Unit)
}

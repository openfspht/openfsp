package org.openfsp.validator.run.api

import org.openfsp.validator.engine.Reply
import java.util.UUID

interface ReceiveRunWebhooks {
    fun knows(id: UUID): Boolean

    fun receive(
        id: UUID,
        method: String,
        rest: String,
        headers: Map<String, List<String>>,
        body: ByteArray,
    ): Reply?
}

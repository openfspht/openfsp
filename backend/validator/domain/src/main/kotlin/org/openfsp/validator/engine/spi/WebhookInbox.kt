package org.openfsp.validator.engine.spi

import org.openfsp.validator.engine.Delivery
import org.openfsp.validator.engine.Reply
import org.openfsp.validator.json.Json

interface WebhookInbox {
    val url: String

    fun deliveries(): List<Delivery>

    fun instruct(
        where: Map<String, Json>,
        reply: Reply,
        times: Int?,
    )
}

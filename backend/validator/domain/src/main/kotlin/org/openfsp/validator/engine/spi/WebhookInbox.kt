package org.openfsp.validator.engine.spi

import org.openfsp.validator.engine.Delivery
import org.openfsp.validator.engine.Reply
import tools.jackson.databind.JsonNode

interface WebhookInbox {
    val url: String

    fun deliveries(): List<Delivery>

    fun instruct(
        where: Map<String, JsonNode>,
        reply: Reply,
        times: Int?,
    )
}

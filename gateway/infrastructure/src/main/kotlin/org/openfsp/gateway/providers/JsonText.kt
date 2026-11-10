package org.openfsp.gateway.providers

import tools.jackson.databind.JsonNode

fun JsonNode.text(): String? = takeUnless { it.isMissingNode || it.isNull }?.asString()

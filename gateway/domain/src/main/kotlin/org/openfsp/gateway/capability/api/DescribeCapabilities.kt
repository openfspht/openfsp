package org.openfsp.gateway.capability.api

import org.openfsp.gateway.capability.ProviderCapabilities

interface DescribeCapabilities {
    fun describe(): List<ProviderCapabilities>
}

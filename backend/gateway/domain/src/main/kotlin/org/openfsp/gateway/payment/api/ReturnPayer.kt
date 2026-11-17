package org.openfsp.gateway.payment.api

import java.net.URI

interface ReturnPayer {
    fun returnTarget(
        providerId: String,
        parameters: Map<String, String>,
    ): URI?
}

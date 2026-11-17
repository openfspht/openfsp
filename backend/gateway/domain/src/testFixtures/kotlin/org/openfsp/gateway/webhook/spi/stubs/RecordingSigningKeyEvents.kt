package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.spi.SigningKeyEvents

@Stub
class RecordingSigningKeyEvents : SigningKeyEvents {
    val rotated = mutableListOf<String>()

    override fun rotated(kid: String) {
        this.rotated += kid
    }
}

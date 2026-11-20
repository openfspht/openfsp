package org.openfsp.validator.attestations

import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.attestation.spi.SignedPayload
import org.openfsp.validator.shared.json.CanonicalJson
import org.openfsp.validator.shared.json.ValidatorJson
import tools.jackson.databind.JsonNode
import java.security.PublicKey
import java.security.Signature
import org.springframework.stereotype.Component
import java.util.Base64

class ParsedJws(
    val header: JsonNode,
    override val payload: ByteArray,
    val signingInput: ByteArray,
    val signature: ByteArray,
) : SignedPayload {
    override val kid: String? get() = header.path(KID).stringValue(null)

    override fun verifiedBy(publicKey: PublicKey): Boolean =
        header.path(ALG).stringValue(null) == WebhookJwk.EDDSA &&
            runCatching {
                Signature.getInstance(WebhookJwk.ED25519_CURVE).run {
                    initVerify(publicKey)
                    update(signingInput)
                    verify(signature)
                }
            }.getOrDefault(false)

    companion object {
        const val ALG = "alg"
        const val KID = "kid"
    }
}

@Component
class CompactJws : AttestationSignatures {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    override fun sign(
        payload: ByteArray,
        key: SigningKey,
    ): String {
        val header = CanonicalJson.of(ValidatorJson.documents.valueToTree(linkedMapOf(ParsedJws.ALG to WebhookJwk.EDDSA, "cty" to CONTENT_TYPE, ParsedJws.KID to key.kid, "typ" to TYPE)))
        val signingInput = "${encoder.encodeToString(header.toByteArray(Charsets.UTF_8))}.${encoder.encodeToString(payload)}"
        val signature =
            Signature.getInstance(WebhookJwk.ED25519_CURVE).run {
                initSign(key.privateKey)
                update(signingInput.toByteArray(Charsets.US_ASCII))
                sign()
            }
        return "$signingInput.${encoder.encodeToString(signature)}"
    }

    override fun parse(signed: String): ParsedJws? {
        val parts = signed.trim().split('.')
        if (parts.size != PARTS || parts.any { !BASE64URL.matches(it) }) return null
        return runCatching {
            val header = ValidatorJson.documents.readTree(decoder.decode(parts[0]))
            if (!header.isObject || header.has("crit")) return null
            ParsedJws(header, decoder.decode(parts[1]), "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII), decoder.decode(parts[2]))
        }.getOrNull()
    }

    companion object {
        const val TYPE = "JOSE"
        const val CONTENT_TYPE = "openfsp-attestation+json"
        private const val PARTS = 3
        private val BASE64URL = Regex("[A-Za-z0-9_-]*")
    }
}

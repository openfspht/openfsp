package org.openfsp.protocol.webhook

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class KeyStatus(
    @get:JsonValue val wire: String,
) {
    ACTIVE("active"),
    RETIRED("retired"),
    ;

    companion object {
        fun fromWire(value: String?): KeyStatus? = entries.firstOrNull { it.wire == value }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WebhookJwk(
    val kid: String,
    val kty: String = OKP,
    val crv: String = ED25519_CURVE,
    val alg: String = EDDSA,
    val use: String = SIG,
    val x: String,
    @JsonProperty("openfsp_status") val openfspStatus: String,
) {
    fun publicKeyOrNull(): PublicKey? =
        if (kty == OKP && crv == ED25519_CURVE && alg == EDDSA && use == SIG) runCatching { Ed25519Keys.fromRaw(x) }.getOrNull() else null

    companion object {
        const val OKP = "OKP"
        const val ED25519_CURVE = "Ed25519"
        const val EDDSA = "EdDSA"
        const val SIG = "sig"

        fun of(
            kid: String,
            publicKey: PublicKey,
            status: KeyStatus,
        ) = WebhookJwk(kid = kid, x = Ed25519Keys.toRaw(publicKey), openfspStatus = status.wire)
    }
}

object Ed25519Keys {
    private const val RAW_KEY_BYTES = 32
    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    fun toRaw(publicKey: PublicKey): String {
        val encoded = publicKey.encoded
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.copyOfRange(encoded.size - RAW_KEY_BYTES, encoded.size))
    }

    fun fromRaw(x: String): PublicKey {
        val raw = Base64.getUrlDecoder().decode(x)
        require(raw.size == RAW_KEY_BYTES) { "an Ed25519 public key holds 32 bytes" }
        return KeyFactory.getInstance(ED25519_CURVE).generatePublic(X509EncodedKeySpec(SPKI_PREFIX + raw))
    }

    private const val ED25519_CURVE = "Ed25519"
}

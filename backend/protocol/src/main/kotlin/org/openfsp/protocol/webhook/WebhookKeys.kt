package org.openfsp.protocol.webhook

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.EdECKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
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
    val y: String? = null,
) {
    fun publicKeyOrNull(): PublicKey? = if (use == SIG) publicKeyOf(kty, crv, alg, x, y) else null

    companion object {
        const val OKP = "OKP"
        const val ED25519_CURVE = "Ed25519"
        const val EDDSA = "EdDSA"
        const val EC = "EC"
        const val P256_CURVE = "P-256"
        const val ES256 = "ES256"
        const val SIG = "sig"

        fun publicKeyOf(
            kty: String?,
            crv: String?,
            alg: String?,
            x: String?,
            y: String?,
        ): PublicKey? =
            runCatching {
                when {
                    kty == OKP && crv == ED25519_CURVE && alg in setOf(null, EDDSA) && x != null && y == null -> Ed25519Keys.fromRaw(x)
                    kty == EC && crv == P256_CURVE && alg in setOf(null, ES256) && x != null && y != null -> P256Keys.fromCoordinates(x, y)
                    else -> null
                }
            }.getOrNull()

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

object P256Keys {
    private const val COORDINATE_BYTES = 32
    private val PARAMETERS: ECParameterSpec =
        AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }

    fun fromCoordinates(
        x: String,
        y: String,
    ): PublicKey {
        val point = ECPoint(coordinate(x), coordinate(y))
        require(isOnCurve(point)) { "the point is not on P-256" }
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, PARAMETERS))
    }

    fun toCoordinates(publicKey: ECPublicKey): Pair<String, String> = encode(publicKey.w.affineX) to encode(publicKey.w.affineY)

    fun isP256(key: Key): Boolean {
        val params = (key as? ECPublicKey)?.params ?: return false
        return params.curve == PARAMETERS.curve && params.generator == PARAMETERS.generator && params.order == PARAMETERS.order
    }

    private fun coordinate(value: String): BigInteger {
        val raw = Base64.getUrlDecoder().decode(value)
        require(raw.size == COORDINATE_BYTES) { "a P-256 coordinate holds 32 bytes" }
        return BigInteger(1, raw)
    }

    private fun isOnCurve(point: ECPoint): Boolean {
        val curve = PARAMETERS.curve
        val p = (curve.field as ECFieldFp).p
        if (point.affineX >= p || point.affineY >= p) return false
        val left = point.affineY.modPow(BigInteger.TWO, p)
        val right = point.affineX.modPow(BigInteger.valueOf(3), p).add(curve.a.multiply(point.affineX)).add(curve.b).mod(p)
        return left == right
    }

    private fun encode(value: BigInteger): String {
        val bytes = value.toByteArray().let { if (it.size > COORDINATE_BYTES) it.copyOfRange(it.size - COORDINATE_BYTES, it.size) else it }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(COORDINATE_BYTES - bytes.size) + bytes)
    }
}

enum class WebhookAlgorithm(
    val wire: String,
    internal val jca: String,
) {
    ED25519("ed25519", "Ed25519"),
    ECDSA_P256_SHA256("ecdsa-p256-sha256", "SHA256withECDSAinP1363Format"),
    ;

    companion object {
        fun of(key: Key): WebhookAlgorithm? =
            when {
                (key as? EdECKey)?.params?.name.equals("Ed25519", ignoreCase = true) -> ED25519
                P256Keys.isP256(key) -> ECDSA_P256_SHA256
                else -> null
            }
    }
}

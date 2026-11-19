package org.openfsp.validator.engine

import org.openfsp.ddd.DomainService
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.WebhookJwk
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

@DomainService
class JwkKeys {
    fun ed25519(
        crv: String?,
        alg: String?,
        x: String?,
    ): PublicKey? {
        if (crv != WebhookJwk.ED25519_CURVE || alg != WebhookJwk.EDDSA || x == null) return null
        return runCatching { Ed25519Keys.fromRaw(x) }.getOrNull()
    }

    fun p256(
        crv: String?,
        alg: String?,
        x: String?,
        y: String?,
    ): PublicKey? {
        if (crv != P256 || alg != ES256 || x == null || y == null) return null
        return runCatching {
            val point = ECPoint(coordinate(x), coordinate(y))
            val parameters = AlgorithmParameters.getInstance(EC).apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
            require(isOnCurve(point, parameters)) { "the point is not on P-256" }
            KeyFactory.getInstance(EC).generatePublic(ECPublicKeySpec(point, parameters))
        }.getOrNull()
    }

    private fun isOnCurve(
        point: ECPoint,
        parameters: ECParameterSpec,
    ): Boolean {
        val p = (parameters.curve.field as ECFieldFp).p
        val (x, y) = point.affineX to point.affineY
        if (x.signum() < 0 || x >= p || y.signum() < 0 || y >= p) return false
        val right = x.pow(3).add(parameters.curve.a.multiply(x)).add(parameters.curve.b).mod(p)
        return y.pow(2).mod(p) == right
    }

    private fun coordinate(value: String): BigInteger {
        val bytes = Base64.getUrlDecoder().decode(value)
        require(bytes.size == COORDINATE_BYTES) { "a P-256 coordinate holds 32 bytes" }
        return BigInteger(1, bytes)
    }

    companion object {
        const val EC = "EC"
        const val P256 = "P-256"
        const val ES256 = "ES256"
        private const val COORDINATE_BYTES = 32
    }
}

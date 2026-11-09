package org.openfsp.gateway.webhook

import org.openfsp.gateway.GatewayProperties
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.sql.Timestamp
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

data class Jwk(
    val kid: String,
    val kty: String = "OKP",
    val crv: String = "Ed25519",
    val alg: String = "EdDSA",
    val use: String = "sig",
    val x: String,
    val openfspStatus: String,
)

data class JwkSet(
    val keys: List<Jwk>,
)

@Component
class SigningKeys(
    private val jdbc: JdbcClient,
    private val properties: GatewayProperties,
    private val clock: Clock,
) {
    private data class StoredKey(
        val kid: String,
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val status: String,
    )

    @Volatile
    private var signer: WebhookSigner? = null

    fun signer(): WebhookSigner =
        signer ?: synchronized(this) {
            signer ?: run {
                val active = stored().firstOrNull { it.status == ACTIVE } ?: generate()
                WebhookSigner(decodePrivate(active.privateKey), active.kid, clock).also { signer = it }
            }
        }

    fun published(): JwkSet = JwkSet(stored().map { Jwk(kid = it.kid, x = rawPublicKey(it.publicKey), openfspStatus = it.status) })

    private fun stored(): List<StoredKey> =
        jdbc
            .sql("select kid, private_key, public_key, status from webhook_signing_keys order by created_at desc")
            .query { rs, _ -> StoredKey(rs.getString(1), rs.getBytes(2), rs.getBytes(3), rs.getString(4)) }
            .list()

    private fun generate(): StoredKey {
        check(properties.developmentMode) { "no active webhook signing key: provision one before serving in this environment" }
        val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
        val key = StoredKey("gw-" + KID_FORMAT.format(clock.instant()), pair.private.encoded, pair.public.encoded, ACTIVE)
        jdbc
            .sql("insert into webhook_signing_keys (kid, private_key, public_key, status, created_at) values (?, ?, ?, ?, ?) on conflict do nothing")
            .params(key.kid, key.privateKey, key.publicKey, key.status, Timestamp.from(clock.instant()))
            .update()
        return stored().first { it.status == ACTIVE }
    }

    private fun decodePrivate(encoded: ByteArray): PrivateKey = KeyFactory.getInstance(ED25519).generatePrivate(PKCS8EncodedKeySpec(encoded))

    private fun rawPublicKey(encoded: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.copyOfRange(encoded.size - RAW_KEY_BYTES, encoded.size))

    private companion object {
        const val ED25519 = "Ed25519"
        const val ACTIVE = "active"
        const val RAW_KEY_BYTES = 32
        val KID_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC)
    }
}

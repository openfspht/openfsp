package org.openfsp.validator.attestations.signing

import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.attestation.spi.SigningKeys
import org.openfsp.validator.shared.configuration.SigningKeyProperties
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.EdECPrivateKey
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.io.path.readText

data class AttestationJwk(
    val kid: String,
    val kty: String = WebhookJwk.OKP,
    val crv: String = WebhookJwk.ED25519_CURVE,
    val alg: String = WebhookJwk.EDDSA,
    val use: String = WebhookJwk.SIG,
    val x: String,
    val status: String,
) {
    companion object {
        fun of(key: AttestationKey) = AttestationJwk(kid = key.kid, x = Ed25519Keys.toRaw(key.publicKey), status = key.status.wire)
    }
}

interface AttestationKeyStore {
    fun all(): List<AttestationKey>

    fun find(kid: String): AttestationKey?

    fun activate(
        kid: String,
        publicKey: PublicKey,
        privateKey: ByteArray?,
    )

    fun developmentKey(): SigningKey?
}

object Ed25519KeyMaterial {
    private const val ED25519 = "Ed25519"
    private const val PEM_BEGIN = "-----BEGIN PRIVATE KEY-----"
    private const val PEM_END = "-----END PRIVATE KEY-----"

    fun generate(): KeyPair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()

    fun privateKey(text: String): PrivateKey {
        val base64 = text.trim().removePrefix(PEM_BEGIN).removeSuffix(PEM_END).filterNot(Char::isWhitespace)
        return KeyFactory.getInstance(ED25519).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
    }

    fun privateKey(encoded: ByteArray): PrivateKey = KeyFactory.getInstance(ED25519).generatePrivate(PKCS8EncodedKeySpec(encoded))

    fun publicKeyOf(privateKey: PrivateKey): PublicKey {
        val seed = (privateKey as? EdECPrivateKey)?.bytes?.orElse(null) ?: throw IllegalArgumentException("the attestation key must be an Ed25519 private key")
        // The JDK cannot derive an Ed25519 public key directly; a generator fed the seed rebuilds the same pair.
        val replay =
            object : SecureRandom() {
                override fun nextBytes(bytes: ByteArray) {
                    seed.copyInto(bytes)
                }
            }
        return KeyPairGenerator.getInstance(ED25519).apply { initialize(NamedParameterSpec.ED25519, replay) }.generateKeyPair().public
    }

    fun thumbprint(publicKey: PublicKey): String {
        val members = """{"crv":"${WebhookJwk.ED25519_CURVE}","kty":"${WebhookJwk.OKP}","x":"${Ed25519Keys.toRaw(publicKey)}"}"""
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(members.toByteArray(Charsets.UTF_8)))
    }
}

@Component
class AttestationKeys(
    private val store: AttestationKeyStore,
    private val properties: ValidatorProperties,
) : SigningKeys {
    private val log = LoggerFactory.getLogger(javaClass)

    private val signing: SigningKey? by lazy { resolve() }

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        val key = signing
        if (key == null) log.warn("no attestation signing key is configured: set openfsp.validator.attestation.signing-key.private-key-file to issue attestations")
        else log.info("attestations are signed with key {}", key.kid)
    }

    override fun signing(): SigningKey? = signing

    override fun published(): List<AttestationKey> = store.all().sortedWith(compareBy<AttestationKey> { it.status != KeyStatus.ACTIVE }.thenByDescending { it.createdAt })

    override fun find(kid: String): AttestationKey? = store.find(kid)

    private fun resolve(): SigningKey? {
        properties.attestation.signingKey?.let(::configured)?.let { return it }
        if (!properties.developmentMode) return null
        store.developmentKey()?.let { return it }
        val pair = Ed25519KeyMaterial.generate()
        val kid = Ed25519KeyMaterial.thumbprint(pair.public)
        store.activate(kid, pair.public, pair.private.encoded)
        log.info("generated the development attestation key {}", kid)
        return SigningKey(kid, pair.private)
    }

    private fun configured(key: SigningKeyProperties): SigningKey? {
        val text = key.privateKey?.takeIf { it.isNotBlank() } ?: key.privateKeyFile?.readText() ?: return null
        val privateKey = Ed25519KeyMaterial.privateKey(text)
        val publicKey = Ed25519KeyMaterial.publicKeyOf(privateKey)
        val kid = key.id?.takeIf { it.isNotBlank() } ?: Ed25519KeyMaterial.thumbprint(publicKey)
        store.activate(kid, publicKey, null)
        return SigningKey(kid, privateKey)
    }
}

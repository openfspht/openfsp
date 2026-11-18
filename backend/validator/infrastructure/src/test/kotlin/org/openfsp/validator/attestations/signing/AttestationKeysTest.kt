package org.openfsp.validator.attestations.signing

import java.nio.file.Files
import java.security.PublicKey
import java.time.Instant
import java.util.Base64
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.shared.configuration.AttestationProperties
import org.openfsp.validator.shared.configuration.SigningKeyProperties
import org.openfsp.validator.shared.configuration.ValidatorProperties

class AttestationKeysTest {
    private class MemoryKeys : AttestationKeyStore {
        val keys = linkedMapOf<String, Pair<AttestationKey, ByteArray?>>()

        override fun all() = keys.values.map { it.first }

        override fun find(kid: String) = keys[kid]?.first

        override fun activate(
            kid: String,
            publicKey: PublicKey,
            privateKey: ByteArray?,
        ) {
            keys.replaceAll { _, (key, secret) -> key.copy(status = KeyStatus.RETIRED) to secret }
            keys[kid] = AttestationKey(kid, publicKey, KeyStatus.ACTIVE, Instant.now()) to privateKey
        }

        override fun developmentKey() =
            keys.values.firstOrNull { it.first.status == KeyStatus.ACTIVE && it.second != null }?.let { SigningKey(it.first.kid, Ed25519KeyMaterial.privateKey(it.second!!)) }

        override fun erasePrivateKeys(): Int {
            val held = keys.values.count { it.second != null }
            keys.replaceAll { _, (key, _) -> key to null }
            return held
        }
    }

    private fun keys(
        store: AttestationKeyStore,
        developmentMode: Boolean,
        signingKey: SigningKeyProperties? = null,
        revokedKids: Set<String> = emptySet(),
    ) = AttestationKeys(store, ValidatorProperties(developmentMode = developmentMode, attestation = AttestationProperties(signingKey = signingKey, revokedKids = revokedKids)))

    @Test
    fun `a revoked key is neither published nor found, and a configured key that is revoked never signs`() {
        val store = MemoryKeys()
        val development = keys(store, developmentMode = true).signing()!!
        val file = Files.createTempFile("attestation", ".pem").apply { writeText(Base64.getEncoder().encodeToString(Ed25519KeyMaterial.generate().private.encoded)) }
        val replaced = keys(store, developmentMode = false, SigningKeyProperties(privateKeyFile = file), revokedKids = setOf(development.kid))

        assertNotNull(replaced.signing())
        assertEquals(listOf(replaced.signing()!!.kid), replaced.published().map { it.kid })
        assertNull(replaced.find(development.kid))
        val revokedSigner = keys(store, developmentMode = false, SigningKeyProperties(id = "revoked", privateKeyFile = file), revokedKids = setOf("revoked"))
        assertFailsWith<IllegalStateException> { revokedSigner.signing() }
    }

    @Test
    fun `outside development mode the private keys stored in the database are erased and none is generated`() {
        val store = MemoryKeys()
        keys(store, developmentMode = true).signing()
        val production = keys(store, developmentMode = false)

        production.announce()

        assertNull(store.developmentKey())
        assertNull(production.signing())
        assertEquals(listOf(null), store.keys.values.map { it.second })
    }

    @Test
    fun `a key from a secrets file signs and the key it replaces stays published as retired`() {
        val store = MemoryKeys()
        val development = keys(store, developmentMode = true).signing()!!
        val pair = Ed25519KeyMaterial.generate()
        val file = Files.createTempFile("attestation", ".pem")
        file.writeText("-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(pair.private.encoded)}\n-----END PRIVATE KEY-----\n")
        val configured = keys(store, developmentMode = true, SigningKeyProperties(privateKeyFile = file))
        val signing = configured.signing()!!
        assertEquals(Ed25519KeyMaterial.thumbprint(pair.public), signing.kid)
        assertNull(store.keys[signing.kid]!!.second)
        assertEquals(listOf(signing.kid to KeyStatus.ACTIVE, development.kid to KeyStatus.RETIRED), configured.published().map { it.kid to it.status })
    }

    @Test
    fun `development mode keeps its generated key across restarts and production without a key issues nothing`() {
        val store = MemoryKeys()
        val first = keys(store, developmentMode = true).signing()
        assertNotNull(first)
        assertEquals(first.kid, keys(store, developmentMode = true).signing()?.kid)
        assertNull(keys(MemoryKeys(), developmentMode = false).signing())
        val named = keys(MemoryKeys(), developmentMode = false, SigningKeyProperties(id = "validator-2026", privateKey = Base64.getEncoder().encodeToString(Ed25519KeyMaterial.generate().private.encoded)))
        assertEquals("validator-2026", named.signing()?.kid)
    }
}

package org.openfsp.validator.attestations.signing

import org.junit.jupiter.api.Test
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.AttestationVerifier
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.reports.json.JsonReportFormat
import org.openfsp.validator.shared.json.CanonicalJson
import org.openfsp.validator.shared.json.JacksonJsonCodec
import org.openfsp.validator.shared.json.ValidatorJson
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttestationSignatureTest {
    private val pair = Ed25519KeyMaterial.generate()
    private val kid = Ed25519KeyMaterial.thumbprint(pair.public)
    private val signing = SigningKey(kid, pair.private)
    private val published = AttestationKey(kid, pair.public, KeyStatus.ACTIVE, Instant.EPOCH)
    private val signatures = CompactJws()
    private val verifier = AttestationVerifier({ if (it == kid) published else null }, signatures, JacksonJsonCodec(), JsonReportFormat())

    private val report = """{"core":"FAIL","results":[{"verdict":"FAIL"}],"duration_ms":12}"""
    private val digest = CanonicalJson.sha256(ValidatorJson.documents.readTree(report))

    private fun payload(reportDigest: String = digest) =
        CanonicalJson
            .of(ValidatorJson.documents.readTree("""{"type":"${Attestation.TYPE}","id":"a-1","report":{"id":"r-1","digest":"$reportDigest"}}"""))
            .toByteArray()

    @Test
    fun `a signed attestation verifies with the published key and the stored report`() {
        val jws = signatures.sign(payload(), signing)
        val result = verifier.verify(jws, report, "a-1", "r-1")
        assertTrue(result.valid, result.problems.toString())
        assertTrue(result.signatureValid)
        assertTrue(result.keyKnown)
        assertEquals("active", result.keyStatus)
        assertTrue(result.digestMatches)
        val header = ValidatorJson.documents.readTree(Base64.getUrlDecoder().decode(jws.substringBefore('.')))
        assertEquals("EdDSA", header.path("alg").asString())
        assertEquals(kid, header.path("kid").asString())
    }

    @Test
    fun `a tampered payload is refused`() {
        val jws = signatures.sign(payload(), signing)
        val (header, _, signature) = jws.split('.')
        val forged = Base64.getUrlEncoder().withoutPadding().encodeToString(String(payload()).replace("a-1", "a-2").toByteArray())
        val result = verifier.verify("$header.$forged.$signature", report)
        assertFalse(result.valid)
        assertFalse(result.signatureValid)
    }

    @Test
    fun `a report that no longer matches the attested digest is refused`() {
        val jws = signatures.sign(payload(), signing)
        val result = verifier.verify(jws, report.replace("FAIL", "PASS"))
        assertFalse(result.valid)
        assertTrue(result.signatureValid)
        assertFalse(result.digestMatches)
        assertFalse(verifier.verify(signatures.sign(payload(reportDigest = "0".repeat(64)), signing), report).digestMatches)
    }

    @Test
    fun `a signature by an unknown key is refused`() {
        val other = Ed25519KeyMaterial.generate()
        val jws = signatures.sign(payload(), SigningKey("unknown", other.private))
        val result = verifier.verify(jws, report)
        assertFalse(result.valid)
        assertFalse(result.keyKnown)
        assertFalse(result.signatureValid)
        val impostor = signatures.sign(payload(), SigningKey(kid, other.private))
        assertFalse(verifier.verify(impostor, report).signatureValid)
        assertNull(signatures.parse("not.a-jws"))
    }

    @Test
    fun `a private key in PEM yields its public key and a thumbprint key id`() {
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder().encodeToString(pair.private.encoded)}\n-----END PRIVATE KEY-----\n"
        val loaded = Ed25519KeyMaterial.privateKey(pem)
        assertEquals(pair.public, Ed25519KeyMaterial.publicKeyOf(loaded))
        assertEquals(43, kid.length)
    }

    @Test
    fun `canonical JSON sorts members, drops whitespace and writes numbers as ECMAScript does`() {
        val node = ValidatorJson.documents.readTree("""{ "b": [1, 2.50, 1e21, 0.0000001], "a": "é\n\u0001", "c": { "z": true, "y": null } }""")
        assertEquals("""{"a":"é\n\u0001","b":[1,2.5,1e+21,1e-7],"c":{"y":null,"z":true}}""", CanonicalJson.of(node))
    }
}

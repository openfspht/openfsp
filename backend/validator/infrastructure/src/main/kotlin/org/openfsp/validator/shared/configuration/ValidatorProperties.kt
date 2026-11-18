package org.openfsp.validator.shared.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("openfsp.validator")
data class ValidatorProperties(
    val developmentMode: Boolean = false,
    val conformance: Path? = null,
    val specUrl: String = "https://openfsp.org/docs/spec/",
    val requestTimeout: Duration = Duration.ofSeconds(30),
    val retention: Duration = Duration.ofDays(90),
    val purgeInterval: Duration = Duration.ofHours(6),
    val purgeDelay: Duration = Duration.ofMinutes(1),
    val sessionSweepInterval: Duration = Duration.ofMinutes(1),
    val eventTimeout: Duration = Duration.ofMinutes(30),
    val finishedRunMemory: Duration = Duration.ofMinutes(15),
    val runsPerAddress: Int = 6,
    val runsWindow: Duration = Duration.ofHours(1),
    val concurrency: Int = 4,
    val preparedRunLifetime: Duration = Duration.ofHours(1),
    val publicUrl: String = PUBLIC_VALIDATOR,
    val webhookCapacity: Int = 500,
    val webhookBytes: Long = 16L shl 20,
    val maxPreparedRuns: Int = 64,
    val environment: String = "test",
    val keyPrefix: String = "ofsp_test_",
    val attestation: AttestationProperties = AttestationProperties(),
)

private const val PUBLIC_VALIDATOR = "https://validate.openfsp.org"

data class AttestationProperties(
    val publicUrl: String = PUBLIC_VALIDATOR,
    val signingKey: SigningKeyProperties? = null,
    val revokedKids: Set<String> = emptySet(),
)

data class SigningKeyProperties(
    val id: String? = null,
    val privateKey: String? = null,
    val privateKeyFile: Path? = null,
)

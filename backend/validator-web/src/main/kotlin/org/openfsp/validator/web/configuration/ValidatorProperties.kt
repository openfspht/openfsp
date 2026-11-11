package org.openfsp.validator.web.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("openfsp.validator")
data class ValidatorProperties(
    val developmentMode: Boolean = false,
    val conformance: Path? = null,
    val specUrl: String = "https://github.com/openfspht/openfsp/blob/main/spec/",
    val requestTimeout: Duration = Duration.ofSeconds(30),
    val retention: Duration = Duration.ofDays(90),
    val eventTimeout: Duration = Duration.ofMinutes(30),
    val finishedRunMemory: Duration = Duration.ofMinutes(15),
    val runsPerAddress: Int = 6,
    val runsWindow: Duration = Duration.ofHours(1),
    val concurrency: Int = 4,
    val preparedRunLifetime: Duration = Duration.ofHours(1),
    val publicUrl: String = "http://127.0.0.1:8095",
    val webhookCapacity: Int = 500,
    val simulator: SimulatorProperties = SimulatorProperties(),
    val attestation: AttestationProperties = AttestationProperties(),
)

data class AttestationProperties(
    val publicUrl: String = "http://127.0.0.1:5176",
    val signingKey: SigningKeyProperties? = null,
)

data class SigningKeyProperties(
    val id: String? = null,
    val privateKey: String? = null,
    val privateKeyFile: Path? = null,
)

data class SimulatorProperties(
    val controlUrl: String = "http://127.0.0.1:8090",
    val publicUrl: String = "http://127.0.0.1:8090",
    val environment: String = "test",
    val keyPrefix: String = "ofsp_test_",
    val derivationKey: String? = null,
    val operators: Map<String, SimulatedOperatorProperties> = emptyMap(),
)

data class SimulatedOperatorProperties(
    val dialect: String,
    val displayName: String? = null,
    val basePath: String,
    val secondaryPath: String? = null,
    val credentials: Map<String, String> = emptyMap(),
)

package org.openfsp.validator.run

import java.time.Duration

data class SimulatedOperator(
    val basePath: String,
    val nativeInputs: Map<String, String> = emptyMap(),
)

data class SimulatorSettings(
    val publicUrl: String,
    val environment: String,
    val keyPrefix: String,
    val derivationKey: String?,
    val operators: Map<String, SimulatedOperator> = emptyMap(),
)

data class RunSettings(
    val requestTimeout: Duration,
    val finishedRunMemory: Duration,
    val runsPerAddress: Int,
    val runsWindow: Duration,
    val concurrency: Int,
    val preparedRunLifetime: Duration,
    val publicUrl: String,
    val webhookCapacity: Int,
    val simulator: SimulatorSettings,
)

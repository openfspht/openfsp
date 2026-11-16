package org.openfsp.validator

import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.SuiteLoader
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object TestSuites {
    fun things(): Suite {
        val directory = Files.createTempDirectory("conformance")
        directory.resolve("requirements.yaml").writeText(
            """
            suite:
              version: 0.0.1
              protocol-version: 0.1.0
              discovery:
                path: /.well-known/openfsp-configuration
                protocol-version: /protocol_version
              capabilities:
                path: /v1/capabilities
                providers: /providers
                id: /id
                names: /capabilities
              journal-settle: 0ms
            limits:
              - reference: conformance#5.6
                text: signatures are not verified by this report
            requirements:
              - { id: T-1, requirement: things#1, level: core, targets: [gateway, native-operator], title: one }
              - { id: T-2, requirement: things#2, level: core, targets: [gateway], title: two }
            """.trimIndent(),
        )
        val things = directory.resolve("scenarios/things").createDirectories()
        things.resolve("pass.yaml").writeText(
            """
            requirement: T-1
            title: reads a thing
            steps:
              - simulator: { default: { operator: mock_openfsp, scenario: SUCCESS } }
              - request: { method: GET, path: /v1/things }
                expect: { status: 200, equals: { /value: 1 } }
            """.trimIndent(),
        )
        things.resolve("assisted.yaml").writeText(
            """
            requirement: T-2
            title: needs a restricted key
            assisted: { action: provide a restricted key, inputs: [restricted-key] }
            steps:
              - request: { method: GET, path: /v1/things, auth: "{{input.restricted-key}}" }
                expect: { status: 200 }
            """.trimIndent(),
        )
        return SuiteLoader.load(directory)
    }

    fun target(value: Int = 1) =
        ScriptedTransport(
            mapOf(
                "GET /.well-known/openfsp-configuration" to (200 to """{"protocol_version":"0.1.0"}"""),
                "GET /v1/capabilities" to (200 to """{"providers":[{"id":"mock_openfsp","capabilities":["payments"]}]}"""),
                "GET /v1/things" to (200 to """{"value":$value}"""),
            ),
        )
}

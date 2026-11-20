package org.openfsp.validator

import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Durations
import org.openfsp.validator.engine.Pointers
import org.openfsp.validator.engine.spi.stubs.KnownSchemaCheckers
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.engine.spi.stubs.SteppedTime
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.suite.Assistance
import org.openfsp.validator.suite.CapabilitiesProbe
import org.openfsp.validator.suite.Catalogue
import org.openfsp.validator.suite.ControlAction
import org.openfsp.validator.suite.DiscoveryProbe
import org.openfsp.validator.suite.Expectation
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.ReferenceScenario
import org.openfsp.validator.suite.RequestSpec
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Step
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.SuiteInfo
import org.openfsp.validator.suite.TargetKind
import java.util.concurrent.Executors

object TestSuites {
    fun runner(time: SteppedTime = SteppedTime()) = ConformanceRunner(KnownSchemaCheckers(), PlainJsonCodec, time, time, Executors.newVirtualThreadPerTaskExecutor(), Checks(Pointers()), Pointers(), Durations())

    fun things(): Suite {
        val catalogue =
            Catalogue(
                suite =
                    SuiteInfo(
                        version = "0.0.1",
                        protocolVersion = "0.1.0",
                        discovery = DiscoveryProbe("/.well-known/openfsp-configuration", "/protocol_version"),
                        capabilities = CapabilitiesProbe("/v1/capabilities", "/providers", "/id", "/capabilities"),
                    ),
                limits = listOf(Limit("conformance#8.1", "signatures are not verified by this report")),
                requirements =
                    listOf(
                        Requirement("T-1", "things#1", Requirement.CORE, listOf(TargetKind.SERVER), "one"),
                        Requirement("T-2", "things#2", Requirement.CORE, listOf(TargetKind.SERVER), "two"),
                    ),
            )
        val things = RequestSpec(method = "GET", path = "/v1/things")
        val assisted =
            Scenario(
                requirement = "T-2",
                title = "needs a restricted key",
                assisted = Assistance("provide a restricted key", listOf("restricted-key")),
                steps = listOf(Step(request = things.copy(auth = "{{input.restricted-key}}"), expect = Expectation(status = 200))),
                id = "things/assisted",
            )
        val pass =
            Scenario(
                requirement = "T-1",
                title = "reads a thing",
                steps =
                    listOf(
                        Step(control = ControlAction(declare = ReferenceScenario("order-1", "SUCCESS"))),
                        Step(request = things, expect = Expectation(status = 200, equals = mapOf("/value" to JsonNumber.of(1)))),
                    ),
                id = "things/pass",
            )
        return Suite(catalogue, listOf(assisted, pass), emptyMap())
    }

    const val CONTROL = "/conformance/control"

    fun target(
        value: Int = 1,
        declared: Int = 204,
    ) = ScriptedTransport(
            mapOf(
                "PUT $CONTROL/references/order-1/scenario" to (declared to ""),
                "GET /.well-known/openfsp-configuration" to (200 to """{"protocol_version":"0.1.0"}"""),
                "GET /v1/capabilities" to (200 to """{"providers":[{"id":"mock_openfsp","capabilities":["payments"]}]}"""),
                "GET /v1/things" to (200 to """{"value":$value}"""),
            ),
        )
}

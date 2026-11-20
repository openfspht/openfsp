package org.openfsp.validator.cli

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.RunResult
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.TargetKind
import java.time.Instant
import org.openfsp.validator.report.api.PlannedRun
import org.openfsp.validator.report.api.RunSuite
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.report.Report
import org.openfsp.validator.suite.Suite
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateCommandTest {
    private val conformance = System.getProperty("openfsp.conformance")
    private val report = Files.createTempFile("report", ".json")
    private val target = listOf("--base-url", "http://127.0.0.1:9", "--api-key", "ofsp_test_key", "--control-endpoint", "http://127.0.0.1:9/conformance/control", "--report", report.toString())

    private fun answering(vararg verdicts: Verdict) =
        object : RunSuite {
            override fun plan(
                suite: Suite,
                configuration: Configuration,
            ): List<PlannedRun> = emptyList()

            override fun run(
                suite: Suite,
                configuration: Configuration,
                target: Transport,
                listener: RunListener,
            ): Report {
                val core = suite.scenarios.filter { suite.requirement(it.requirement, TargetKind.SERVER)?.isCore == true }.take(verdicts.size)
                val results = core.zip(verdicts).map { (scenario, verdict) -> ScenarioResult(scenario, suite.requirement(scenario.requirement, TargetKind.SERVER)!!, verdict) }
                return Reports.of(suite, configuration, RunResult(Instant.now(), Instant.now(), "0.1.0", null, results))
            }
        }

    @Test
    fun `the exit code is 0 when Core passes, 1 when it fails and 2 when it is incomplete`() {
        val arguments = listOf("--conformance", conformance) + target
        assertEquals(ExitCodes.CORE_PASSED, validate(arguments, emptyMap(), answering(Verdict.PASS, Verdict.PASS)))
        assertEquals(ExitCodes.CORE_FAILED, validate(arguments, emptyMap(), answering(Verdict.PASS, Verdict.FAIL)))
        assertEquals(ExitCodes.CORE_INCOMPLETE, validate(arguments, emptyMap(), answering(Verdict.PASS, Verdict.INCONCLUSIVE)))
        assertEquals(ExitCodes.CORE_INCOMPLETE, validate(arguments, emptyMap(), answering(Verdict.PASS, Verdict.ASSISTED)))
    }

    @Test
    fun `a manual key needs the control interface on the origin of the server, and no client target or assisted exit is offered`() {
        val base = listOf("--conformance", conformance, "--base-url", "http://127.0.0.1:9", "--api-key", "ofsp_test_key")
        assertEquals(ExitCodes.USAGE, errors { validate(base, emptyMap()) }.first)
        assertEquals(ExitCodes.USAGE, errors { validate(base + listOf("--control-endpoint", "http://elsewhere.test/control"), emptyMap()) }.first)
        assertEquals(ExitCodes.USAGE, errors { validate(listOf("--target", "client") + target.drop(0) + listOf("--conformance", conformance), emptyMap()) }.first)
        assertEquals(ExitCodes.USAGE, errors { validate(listOf("--assisted-ok", "--conformance", conformance) + target, emptyMap()) }.first)
    }

    private fun errors(command: () -> Int): Pair<Int, String> {
        val previous = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            command() to captured.toString(Charsets.UTF_8)
        } finally {
            System.setErr(previous)
        }
    }

    private fun outputs(command: () -> Int): Pair<Int, String> {
        val previous = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            command() to captured.toString(Charsets.UTF_8)
        } finally {
            System.setOut(previous)
        }
    }

    @Test
    fun `an unreadable suite exits with its own code and names the file`() {
        val directory = Files.createTempDirectory("conformance")
        directory.resolve("requirements.yaml").writeText("suite: [")
        val (code, message) = errors { validate(listOf("--conformance", directory.toString()) + target, emptyMap()) }
        assertEquals(ExitCodes.INVALID_SUITE, code)
        assertTrue(message.contains("requirements.yaml"), message)
    }

    @Test
    fun `a timeout that is not a positive number of seconds is a usage error`() {
        listOf("abc", "0", "-5").forEach { timeout ->
            val (code, message) = errors { validate(listOf("--conformance", conformance, "--timeout", timeout) + target, emptyMap()) }
            assertEquals(ExitCodes.USAGE, code, timeout)
            assertTrue(message.contains("--timeout"), message)
        }
    }

    @Test
    fun `a key that is not a test key is refused before any request`() {
        val (code, message) = errors { validate(listOf("--conformance", conformance, "--base-url", "http://127.0.0.1:9", "--api-key", "ofsp_live_key"), emptyMap()) }
        assertEquals(ExitCodes.USAGE, code)
        assertTrue(message.contains("ofsp_test_"), message)
        assertEquals(ExitCodes.USAGE, errors { validate(listOf("--conformance", conformance, "--base-url", "http://127.0.0.1:9"), mapOf("OPENFSP_API_KEY" to "sk_live")) }.first)
    }

    @Test
    fun `an unreachable server exits with its own code and a one line message instead of a core failure`() {
        val (code, message) = errors { validate(listOf("--conformance", conformance, "http://127.0.0.1:9"), emptyMap()) }
        assertEquals(ExitCodes.UNREACHABLE, code)
        assertTrue(message.startsWith("the server under test cannot be reached"), message)
        assertTrue(!message.contains("\tat "), message)
    }

    @Test
    fun `a preparation answer without a control interface is refused`() {
        val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    val origin = "http://127.0.0.1:${address.port}"
                    val (status, body) =
                        when (exchange.requestURI.path) {
                            "/.well-known/openfsp-configuration" -> 200 to """{"protocol_version":"0.1.0","conformance_endpoint":"$origin/conformance"}"""
                            "/conformance" -> 201 to """{"api_key":"ofsp_test_issued","expires_at":"2099-01-01T00:00:00Z"}"""
                            else -> 404 to "{}"
                        }
                    exchange.sendResponseHeaders(status, body.length.toLong())
                    exchange.responseBody.use { it.write(body.toByteArray()) }
                }
                start()
            }
        try {
            val (code, message) = errors { validate(listOf("--conformance", conformance, "--base-url", "http://127.0.0.1:${server.address.port}", "--report", report.toString()), emptyMap()) }
            assertEquals(ExitCodes.REFUSED, code)
            assertTrue(message.contains("control_endpoint"), message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an error inside the engine exits with its own code instead of a core failure`() {
        val failing =
            object : RunSuite {
                override fun plan(
                    suite: Suite,
                    configuration: Configuration,
                ): List<PlannedRun> = emptyList()

                override fun run(
                    suite: Suite,
                    configuration: Configuration,
                    target: Transport,
                    listener: RunListener,
                ): Report = throw IllegalStateException("broken step")
            }
        val (code, message) = errors { validate(listOf("--conformance", conformance) + target, emptyMap(), failing) }
        assertEquals(ExitCodes.INTERNAL, code)
        assertTrue(message.contains("broken step"), message)
    }

    @Test
    fun `the base URL can be given first, the suite comes from the environment and the JSON report only on request`() {
        val positional = listOf("http://127.0.0.1:9", "--api-key", "ofsp_test_key", "--control-endpoint", "http://127.0.0.1:9/conformance/control")
        Files.delete(report)
        assertEquals(ExitCodes.CORE_PASSED, validate(positional, mapOf("OPENFSP_VALIDATOR_CONFORMANCE" to conformance), answering(Verdict.PASS)))
        assertTrue(Files.notExists(report))
        assertEquals(ExitCodes.CORE_PASSED, validate(positional + listOf("--report", report.toString()), mapOf("OPENFSP_VALIDATOR_CONFORMANCE" to conformance), answering(Verdict.PASS)))
        assertTrue(Files.size(report) > 0)
    }

    @Test
    fun `without a directory the suite bundled in the validator is used`() {
        val (code, message) = outputs { validate(listOf("--check-catalogue"), emptyMap()) }
        assertEquals(ExitCodes.CORE_PASSED, code)
        assertTrue(message.startsWith("catalogue: "), message)
    }

    @Test
    fun `a second base URL is a usage error`() {
        val (code, message) = errors { validate(listOf("http://127.0.0.1:9", "--conformance", conformance, "--base-url", "http://127.0.0.1:8"), emptyMap(), answering(Verdict.PASS)) }
        assertEquals(ExitCodes.USAGE, code)
        assertTrue(message.contains("--base-url is given twice"), message)
        assertEquals(ExitCodes.USAGE, errors { validate(listOf("http://127.0.0.1:9", "http://127.0.0.1:8", "--conformance", conformance), emptyMap(), answering(Verdict.PASS)) }.first)
    }

    @Test
    fun `an unwritable report path exits with the internal code after printing the summary`() {
        val directory = Files.createTempDirectory("report")
        val (code, message) = errors { validate(listOf("--conformance", conformance) + target.dropLast(1) + directory.toString(), emptyMap(), answering(Verdict.PASS)) }
        assertEquals(ExitCodes.INTERNAL, code)
        assertTrue(message.contains("cannot be written"), message)
    }
}

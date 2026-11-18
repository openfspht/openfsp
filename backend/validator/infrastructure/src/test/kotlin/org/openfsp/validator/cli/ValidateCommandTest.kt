package org.openfsp.validator.cli

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
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
    private val target = listOf("--base-url", "http://127.0.0.1:9", "--api-key", "ofsp_test_key", "--report", report.toString())

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
}

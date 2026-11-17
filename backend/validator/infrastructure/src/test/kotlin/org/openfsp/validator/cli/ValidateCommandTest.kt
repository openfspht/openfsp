package org.openfsp.validator.cli

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.report.api.PlannedRun
import org.openfsp.validator.report.api.RunSuite
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.report.Report
import org.openfsp.validator.suite.Suite
import java.io.ByteArrayOutputStream
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

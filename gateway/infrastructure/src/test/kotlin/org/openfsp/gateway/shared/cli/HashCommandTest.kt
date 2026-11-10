package org.openfsp.gateway.shared.cli

import org.openfsp.gateway.security.admin.AdminTokenHashing
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashCommandTest {
    private val hashing = AdminTokenHashing()

    private fun run(input: String): Pair<Int, String> {
        val output = ByteArrayOutputStream()
        val code = HashCommand(hashing, input.reader().buffered(), PrintStream(output), PrintStream(ByteArrayOutputStream())).run()
        return code to output.toString().trim()
    }

    @Test
    fun `the command prints the environment line holding the hash`() {
        val (code, line) = run("correct horse battery staple\n")
        assertEquals(0, code)
        val hash = line.removePrefix("OPENFSP_ADMIN_TOKEN='").removeSuffix("'")
        assertTrue(hashing.matches("correct horse battery staple", hash))
    }

    @Test
    fun `the command refuses a short token`() {
        assertEquals(1, run("short\n").first)
    }
}

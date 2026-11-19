package org.openfsp.gateway.shared.cli

import org.openfsp.gateway.shared.security.AdminTokenHashing
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
    fun `the command prints only the Argon2id hash`() {
        val (code, hash) = run("correct horse battery staple\n")
        assertEquals(0, code)
        assertTrue(hash.startsWith(AdminTokenHashing.PREFIX))
        assertEquals(1, hash.lines().size)
        assertTrue(hashing.matches("correct horse battery staple", hash))
    }

    @Test
    fun `the command refuses a short token`() {
        assertEquals(1, run("short\n").first)
    }

    @Test
    fun `the command refuses the published demo token`() {
        assertEquals(1 to "", run("${AdminTokenHashing.DEMO_TOKEN}\n"))
    }
}

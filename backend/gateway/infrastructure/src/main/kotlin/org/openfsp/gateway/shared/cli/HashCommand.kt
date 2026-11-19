package org.openfsp.gateway.shared.cli

import org.openfsp.gateway.shared.security.AdminTokenHashing
import java.io.BufferedReader
import java.io.PrintStream

class HashCommand(
    private val hashing: AdminTokenHashing,
    private val input: BufferedReader = System.`in`.bufferedReader(),
    private val output: PrintStream = System.out,
    private val errors: PrintStream = System.err,
) {
    fun run(): Int {
        val token = read() ?: return FAILURE
        if (token.length < MIN_LENGTH) {
            errors.println("The admin token holds at least $MIN_LENGTH characters.")
            return FAILURE
        }
        if (token == AdminTokenHashing.DEMO_TOKEN) {
            errors.println("The published demo token is refused: choose a token of your own.")
            return FAILURE
        }
        output.println(hashing.hash(token))
        return SUCCESS
    }

    private fun read(): String? {
        val console = System.console()?.takeIf { it.isTerminal } ?: return input.readLine()?.trim()
        val token = String(console.readPassword("Admin token: ") ?: return null)
        val confirmation = String(console.readPassword("Admin token again: ") ?: return null)
        if (token == confirmation) return token
        errors.println("The two entries differ.")
        return null
    }

    companion object {
        const val NAME = "hash"
        private const val MIN_LENGTH = 16
        private const val SUCCESS = 0
        private const val FAILURE = 1
    }
}

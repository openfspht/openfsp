package org.openfsp.gateway.cli

import org.openfsp.gateway.security.admin.AdminTokenHashing
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
        output.println("OPENFSP_ADMIN_TOKEN='${hashing.hash(token)}'")
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

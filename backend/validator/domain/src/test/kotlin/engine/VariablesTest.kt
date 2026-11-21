package org.openfsp.validator.engine

import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VariablesTest {
    private val variables = Variables("run", 0, emptyMap(), emptyMap(), PlainJsonCodec)

    @Test
    fun `a value that is one template of an absent capture resolves to absent`() {
        variables.capture("detail", null)

        assertNull(variables.resolve(JsonString("{{detail}}")))
    }

    @Test
    fun `an absent capture inside a longer text is refused`() {
        variables.capture("detail", null)

        assertFailsWith<TemplateException> { variables.resolve(JsonString("see {{detail}}")) }
    }

    @Test
    fun `a value that is one template of a captured string resolves to that string`() {
        variables.capture("title", JsonString("Unauthenticated"))

        assertEquals(JsonString("Unauthenticated"), variables.resolve(JsonString("{{title}}")))
    }

    @Test
    fun `a date is shifted by a number of days and an integer by its operand`() {
        variables.capture("day", JsonString("2026-03-01"))
        variables.capture("count", JsonString("41"))

        assertEquals("2026-02-28 2026-03-02 42", variables.resolve("{{day - 1}} {{day + 1}} {{count + 1}}"))
        assertEquals(LocalDate.now(ZoneId.of("America/Port-au-Prince")).minusDays(31).toString(), variables.resolve("{{today:America/Port-au-Prince - 31}}"))
        variables.capture("title", JsonString("Unauthenticated"))
        assertFailsWith<TemplateException> { variables.resolve("{{title + 1}}") }
    }

    @Test
    fun `only inputs named like a secret are masked in exchanges`() {
        val inputs = mapOf("bounded-provider" to "mock_moncash", "revoked-key" to "ofsp_test_revoked")

        assertEquals(listOf("ofsp_test_revoked"), Variables("run", 0, inputs, emptyMap(), PlainJsonCodec).secrets.toList())
    }
}

package org.openfsp.validator.engine

import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
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
}

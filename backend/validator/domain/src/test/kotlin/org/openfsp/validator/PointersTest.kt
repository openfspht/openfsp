package org.openfsp.validator

import org.openfsp.validator.engine.Pointers
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointersTest {
    private val document = JsonMapper.builder().build().readTree("""{"errors":[{"field":"/a"},{"field":"/b"}],"a~b":{"c/d":1}}""")

    @Test
    fun `a wildcard selects every element`() {
        assertEquals(listOf("/errors/0/field", "/errors/1/field"), Pointers.resolve(document, "/errors/*/field").map { it.pointer })
    }

    @Test
    fun `escaped tokens resolve and a missing member resolves to nothing`() {
        assertEquals(1, Pointers.resolve(document, "/a~0b/c~1d").single().node.asInt())
        assertTrue(Pointers.resolve(document, "/missing").isEmpty())
        assertTrue(Pointers.resolve(document, "/errors/5").isEmpty())
    }

    @Test
    fun `a member filter selects the array elements whose member equals the value`() {
        assertEquals(listOf("/errors/1/field"), Pointers.resolve(document, "/errors/[field=~1b]/field").map { it.pointer })
        assertTrue(Pointers.resolve(document, "/errors/[field=~1c]").isEmpty())
        val nested = JsonMapper.builder().build().readTree("""{"data":[{"amount":{"amount":1}},{"amount":{"amount":2}}]}""")
        assertEquals(listOf("/data/1"), Pointers.resolve(nested, "/data/[amount~1amount=2]").map { it.pointer })
        val strings = JsonMapper.builder().build().readTree("""{"names":["a","b"]}""")
        assertEquals(listOf("/names/1"), Pointers.resolve(strings, "/names/[=b]").map { it.pointer })
    }
}

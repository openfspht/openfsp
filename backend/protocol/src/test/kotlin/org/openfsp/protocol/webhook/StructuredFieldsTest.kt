package org.openfsp.protocol.webhook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StructuredFieldsTest {
    @Test
    fun `reads a dictionary of inner lists and items`() {
        val members = StructuredFields.parseDictionary("a=(\"x\" \"y\");n=1;s=\"q\\\"t\", b=:AAE=:, c, d=tok/en;f=?0, e=1.50")!!
        assertEquals(listOf("a", "b", "c", "d", "e"), members.keys.toList())
        assertEquals("(\"x\" \"y\");n=1;s=\"q\\\"t\"", StructuredFields.serialize(members.getValue("a") as InnerList))
        assertEquals(listOf<Byte>(0, 1), ((members.getValue("b") as Item).value as BareItem.ByteSequence).value.toList())
        assertEquals(BareItem.BooleanItem(true), (members.getValue("c") as Item).value)
        assertEquals(BareItem.TokenItem("tok/en"), (members.getValue("d") as Item).value)
        assertEquals(BareItem.BooleanItem(false), members.getValue("d").parameters["f"])
    }

    @Test
    fun `a later member replaces an earlier one with the same key`() {
        assertEquals(BareItem.IntegerItem(2), (StructuredFields.parseDictionary("a=1, a=2")!!.getValue("a") as Item).value)
    }

    @Test
    fun `refuses malformed input`() {
        listOf("a=(\"x\"", "A=1", "a=1,", "a=\"unterminated", "a=1234567890123456", "a=:not base64:", "a=1 b=2", "a=\"é\"").forEach {
            assertNull(StructuredFields.parseDictionary(it), it)
        }
    }
}

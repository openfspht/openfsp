package org.openfsp.server

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPointerTest {
    @Test
    fun `a pointer escapes tilde and slash in each token`() {
        assertEquals("/amount/amount", JsonPointer.of("amount", "amount"))
        assertEquals("/metadata/a~1b~0c", JsonPointer.of("metadata", "a/b~c"))
        assertEquals("/items/0", JsonPointer.of("items", "0"))
    }
}

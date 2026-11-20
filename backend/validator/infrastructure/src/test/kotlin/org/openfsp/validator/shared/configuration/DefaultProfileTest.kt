package org.openfsp.validator.shared.configuration

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultProfileTest {
    private val base = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml")).single()

    @Test
    fun `without a chosen profile the validator starts in neither development nor development mode`() {
        assertNull(base.getProperty("spring.profiles.default"))
        assertEquals(false, base.getProperty("openfsp.validator.development-mode"))
        assertEquals(false, ValidatorProperties().developmentMode)
    }

    @Test
    fun `the service and its attestations default to the live validator host`() {
        assertEquals("https://validator.openfsp.org", ValidatorProperties().publicUrl)
    }
}

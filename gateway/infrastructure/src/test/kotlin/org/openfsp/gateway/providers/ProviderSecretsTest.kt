package org.openfsp.gateway.providers

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderSecretsTest {
    private fun moncash(credentials: Map<String, String>) = ProviderProperties.Provider(type = "moncash", baseUrl = URI("https://sandbox.example/Api"), credentials = credentials)

    @Test
    fun `an unresolved placeholder or a blank value is reported as unset`() {
        val provider = moncash(mapOf("client-id" to "\${MONCASH_CLIENT_ID}", "client-secret" to " "))
        assertEquals(listOf("client-id", "client-secret"), provider.unsetSecrets())
    }

    @Test
    fun `set credentials and api key are not reported`() {
        assertEquals(emptyList(), moncash(mapOf("client-id" to "id", "client-secret" to "secret")).unsetSecrets())
        assertEquals(listOf("api-key"), ProviderProperties.Provider(baseUrl = URI("https://operator.example"), apiKey = "\${KEY}").unsetSecrets())
    }
}

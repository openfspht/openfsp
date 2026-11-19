package org.openfsp.ui

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(classes = [MissingBundleTest.Application::class])
@AutoConfigureMockMvc
class MissingBundleTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `an application built without the interface starts and answers 404 on its routes`() {
        listOf("/", "/items", UiPaths.SETTINGS).forEach { path ->
            assertEquals(404, mvc.get(path).andReturn().response.status, path)
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class Application {
        @Bean
        fun userInterface() = UserInterface(listOf("/", "/items"), "default-src 'self'")

        @Bean
        fun indexPage(): IndexPage = IndexPage(Nothing())
    }

    class Nothing : ResourceLoader by DefaultResourceLoader() {
        override fun getResource(location: String): Resource = DefaultResourceLoader().getResource("classpath:/absent/$location")
    }
}

package org.openfsp.ui

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ResourceLoader
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.function.RequestPredicate
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.resource.EncodedResourceResolver
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Duration

@AutoConfiguration
@ConditionalOnBean(UserInterface::class)
@EnableConfigurationProperties(UiProperties::class)
class UserInterfaceConfiguration {
    @Bean
    fun uiPaths(userInterface: UserInterface): UiPaths = UiPaths(userInterface)

    @Bean
    @ConditionalOnMissingBean
    fun indexPage(resources: ResourceLoader): IndexPage = IndexPage(resources)

    @Bean
    fun uiHeadersFilter(
        paths: UiPaths,
        userInterface: UserInterface,
    ): UiHeadersFilter = UiHeadersFilter(paths, userInterface)

    @Bean
    fun uiRoutes(
        userInterface: UserInterface,
        page: IndexPage,
        properties: UiProperties,
    ): RouterFunction<ServerResponse> {
        val routes = RouterFunctions.route()
        userInterface.routes.forEach { route -> routes.route(readable(route)) { request -> index(page, request) } }
        routes.route(readable(UiPaths.SETTINGS)) { request -> if (page.present) settings(properties.sentry) else throw missing(request) }
        return routes.build()
    }

    @Bean
    fun uiAssets(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
                registry
                    .addResourceHandler(UiPaths.ASSETS_PATTERN)
                    .addResourceLocations("${IndexPage.LOCATION}assets/")
                    .setCacheControl(CacheControl.maxAge(ASSET_LIFETIME).cachePublic().immutable())
                    .resourceChain(true)
                    .addResolver(EncodedResourceResolver())
            }
        }

    private fun readable(path: String): RequestPredicate = RequestPredicates.GET(path).or(RequestPredicates.HEAD(path))

    private fun index(
        page: IndexPage,
        request: ServerRequest,
    ): ServerResponse {
        val variant = page.negotiate(request.headers().firstHeader(HttpHeaders.ACCEPT_ENCODING)) ?: throw missing(request)
        return ServerResponse
            .ok()
            .contentType(HTML)
            .cacheControl(CacheControl.noCache())
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING)
            .headers { headers -> variant.coding?.let { headers.set(HttpHeaders.CONTENT_ENCODING, it.token) } }
            .body(variant.body)
    }

    private fun missing(request: ServerRequest) = NoResourceFoundException(request.method(), request.path(), request.path())

    private fun settings(sentry: UiProperties.Sentry): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .body(
                mapOf(
                    "dsn" to sentry.dsn,
                    "environment" to sentry.environment,
                    "release" to sentry.release,
                    "tracesSampleRate" to sentry.tracesSampleRate,
                ),
            )

    private companion object {
        val ASSET_LIFETIME: Duration = Duration.ofDays(365)
        val HTML: MediaType = MediaType("text", "html", Charsets.UTF_8)
    }
}

package org.openfsp.simulator.spaces

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.simulator.operator.space.SpaceId
import org.openfsp.simulator.operator.space.api.ManageSpaces
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class SpaceWebConfiguration : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers += SpaceArgument
    }

    private object SpaceArgument : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == SpaceId::class.java

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?,
        ): SpaceId = SpaceRoutes.of(webRequest.getNativeRequest(HttpServletRequest::class.java)!!)
    }
}

@Component
class SpaceExpiry(
    private val spaces: ManageSpaces,
) {
    @Scheduled(fixedDelayString = "\${simulator.spaces.sweep-interval:1m}")
    fun sweep() {
        spaces.expireIdle()
    }
}

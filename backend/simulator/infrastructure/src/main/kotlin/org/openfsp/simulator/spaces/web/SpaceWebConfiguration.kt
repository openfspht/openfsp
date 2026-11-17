package org.openfsp.simulator.spaces.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.simulator.space.SpaceId
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
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

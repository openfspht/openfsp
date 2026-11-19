package org.openfsp.gateway.apikeys.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class AuthenticatedKeyResolver :
    WebMvcConfigurer,
    HandlerMethodArgumentResolver {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == AuthenticatedKey::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedKey =
        webRequest.getNativeRequest(HttpServletRequest::class.java)?.let(ApiKeyFilter::of)
            ?: throw OpenFspException(ProblemType.UNAUTHENTICATED)
}

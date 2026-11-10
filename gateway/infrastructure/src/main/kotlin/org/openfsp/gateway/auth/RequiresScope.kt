package org.openfsp.gateway.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresScope(
    val value: Scope,
)

@Configuration(proxyBeanMethods = false)
class ScopeEnforcement :
    WebMvcConfigurer,
    HandlerInterceptor {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this).addPathPatterns("/v1/**")
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val required = (handler as? HandlerMethod)?.getMethodAnnotation(RequiresScope::class.java)?.value ?: return true
        val key = ApiKeyFilter.of(request) ?: throw OpenFspException(ProblemType.UNAUTHENTICATED)
        if (required !in key.scopes) throw OpenFspException(ProblemType.FORBIDDEN)
        return true
    }
}

package org.openfsp.gateway.apikeys

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.auth.Scope
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
        registry.addInterceptor(this).addPathPatterns(ApiKeyFilter.PROTECTED_PATHS.map { "$it/**" })
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) return true
        val key = ApiKeyFilter.of(request) ?: throw OpenFspException(ProblemType.UNAUTHENTICATED)
        val required = handler.getMethodAnnotation(RequiresScope::class.java)?.value ?: throw OpenFspException(ProblemType.FORBIDDEN)
        if (required !in key.scopes) throw OpenFspException(ProblemType.FORBIDDEN)
        return true
    }
}

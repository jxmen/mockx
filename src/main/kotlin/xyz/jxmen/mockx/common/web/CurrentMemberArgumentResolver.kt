package xyz.jxmen.mockx.common.web

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentMemberArgumentResolver: HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        val hasParameter = parameter.hasParameterAnnotation(CurrentMember::class.java)
        val isLongType = parameter.parameterType == Long::class.javaPrimitiveType

        return hasParameter && isLongType
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Long {
        val header = webRequest.getHeader("X-Member-Id") ?: throw UnAuthenticatedException()

        val memberId = header.toLongOrNull() ?: throw UnAuthenticatedException()
        if (memberId <= 0) throw UnAuthenticatedException()

        return memberId
    }
}

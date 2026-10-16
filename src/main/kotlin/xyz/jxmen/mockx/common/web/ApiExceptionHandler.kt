package xyz.jxmen.mockx.common.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.exc.MismatchedInputException

@RestControllerAdvice
class ApiExceptionHandler {
    // ProblemDetail을 반환하면 Spring이 status를 응답 상태 코드로 쓰고, application/problem+json으로 응답한다
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(e.status, e.message).apply {
            setProperty("code", e.code)
            e.field?.let { setProperty("field", it) }
        }

    // @Valid 검증 실패. 틀린 필드를 모두 errors에 담는다
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(e: MethodArgumentNotValidException): ProblemDetail =
        invalidInput(
            e.bindingResult.fieldErrors.map { FieldErrorDetail(it.field, it.defaultMessage ?: INVALID_VALUE) },
        )

    // 역직렬화 실패. non-null 필드가 빠졌거나 null이면 MismatchedInputException 계열이고,
    // path 마지막 항목에서 실패한 필드 하나를 알 수 있다. 그 밖(JSON 문법 오류 등)은 errors 없이 응답한다
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ProblemDetail {
        val field = (e.cause as? MismatchedInputException)?.path?.lastOrNull()?.propertyName
        return invalidInput(field?.let { listOf(FieldErrorDetail(it, INVALID_VALUE)) })
    }

    private fun invalidInput(errors: List<FieldErrorDetail>?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.").apply {
            setProperty("code", "INVALID_INPUT")
            errors?.let { setProperty("errors", it) }
        }

    companion object {
        private const val INVALID_VALUE = "값이 없거나 형식이 올바르지 않습니다."
    }
}

data class FieldErrorDetail(
    val field: String,
    val detail: String,
)

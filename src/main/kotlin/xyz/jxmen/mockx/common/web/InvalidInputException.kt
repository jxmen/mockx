package xyz.jxmen.mockx.common.web

/**
 * 요청 DTO 검증(@Valid) 밖에서 찾은 입력값 오류. (예: 쿼리 파라미터 해석)
 * ApiExceptionHandler가 @Valid 실패와 같은 400 INVALID_INPUT, errors 배열로 응답한다.
 * ApiException은 field 하나로 응답하므로 상속하지 않는다.
 */
class InvalidInputException(
    val field: String,
    val detail: String,
) : RuntimeException(detail)

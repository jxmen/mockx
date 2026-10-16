package xyz.jxmen.mockx.common.web

import org.springframework.http.HttpStatus

/**
 * API 에러 응답으로 변환되는 예외의 공통 부모. ApiExceptionHandler가 Problem Details로 바꿔 응답한다.
 * 트랜잭션 안에서 던져지면 롤백되도록 RuntimeException을 상속한다.
 */
abstract class ApiException(
    val status: HttpStatus,
    val code: String,
    val field: String? = null,
    message: String,
) : RuntimeException(message)

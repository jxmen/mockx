package xyz.jxmen.mockx.member

import org.springframework.http.HttpHeaders

/**
 * E2E 테스트에서 받은 HTTP 응답. 4xx, 5xx도 예외 없이 상태 코드, 헤더, JSON 본문을 그대로 담는다.
 */
data class ApiResponse(
    val status: Int,
    val headers: HttpHeaders,
    val body: Map<String, Any?>,
)

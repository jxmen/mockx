package xyz.jxmen.mockx

import io.kotest.matchers.shouldBe
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

// E2E 테스트의 준비 작업. 실패하면 바로 테스트를 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다

private val mapper = jacksonObjectMapper()

private const val MAX_DEPOSIT_AMOUNT = 1_000_000L // 1회 입금 상한 $10,000

/**
 * 가입 API로 회원을 만들고 memberId를 돌려준다. 가입하면 위탁계좌 하나가 함께 개설된다.
 */
fun RestClient.signup(
    username: String,
    nickname: String = username,
): Long {
    val (status, body) =
        post()
            .uri("/api/members")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "username" to username,
                    "nickname" to nickname,
                    "password" to "pass1234",
                    "passwordConfirm" to "pass1234",
                ),
            ).exchange { _, response ->
                response.statusCode.value() to response.bodyTo(String::class.java)
            }
    status shouldBe 201
    return (mapper.readValue<Map<String, Any?>>(body!!)["memberId"] as Number).toLong()
}

/**
 * 회원의 계좌 id. 가입 응답에 없어서 DB에서 직접 꺼낸다.
 */
fun JdbcTemplate.accountIdOf(memberId: Long): Long =
    queryForObject("SELECT id FROM account WHERE member_id = ?", Long::class.java, memberId)!!

/**
 * 입금 API로 계좌에 amount(센트)만큼 입금한다. 1회 입금 상한을 넘으면 나눠서 입금한다.
 */
fun RestClient.fundAccount(
    accountId: Long,
    memberId: Long,
    amount: Long,
) {
    var remaining = amount
    while (remaining > 0) {
        val chunk = minOf(remaining, MAX_DEPOSIT_AMOUNT)
        val status =
            post()
                .uri("/api/accounts/$accountId/deposit")
                .header("X-Member-Id", memberId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("amount" to chunk))
                .exchange { _, response -> response.statusCode.value() }
        status shouldBe 200
        remaining -= chunk
    }
}

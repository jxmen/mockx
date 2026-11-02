package xyz.jxmen.mockx.account

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.accountIdOf
import xyz.jxmen.mockx.ApiResponse
import xyz.jxmen.mockx.signup

/**
 * 입금 API 계약 테스트. 실제 서버(RANDOM_PORT)에 HTTP로 요청하고, Testcontainers MySQL을 쓴다.
 * - amount는 센트 단위 정수다($1 = 100). 1회 입금은 1센트($0.01) 이상 1,000,000센트($10,000) 이하만 허용하고, 소수는 받지 않는다.
 * - 회원과 계좌는 가입 API로 만든다. 설정이 MemberMeE2ETest와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다.
 * - 계약은 docs/db-design-member.md 5장(소유자 검증), 6장(에러 응답)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class DepositE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        val maxAmount = 1_000_000L // $10,000

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다.
        // accountIdPath는 경로에 그대로 들어간다. 숫자가 아닌 id 같은 형식 오류를 보낼 때 문자열을 넘긴다
        fun depositRaw(
            accountIdPath: String,
            json: String,
            memberIdHeader: String?,
        ): ApiResponse =
            client
                .post()
                .uri("/api/accounts/$accountIdPath/deposit")
                .headers { headers -> memberIdHeader?.let { headers.set("X-Member-Id", it) } }
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange { _, response ->
                    val body = response.bodyTo(String::class.java)
                    ApiResponse(
                        status = response.statusCode.value(),
                        headers = response.headers,
                        body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                    )
                }

        fun depositRaw(
            accountId: Long,
            json: String,
            memberIdHeader: String?,
        ): ApiResponse = depositRaw(accountId.toString(), json, memberIdHeader)

        fun deposit(
            accountId: Long,
            amount: Long,
            memberId: Long,
        ): ApiResponse = depositRaw(accountId, mapper.writeValueAsString(mapOf("amount" to amount)), memberId.toString())

        // 입금 성공을 전제로 하는 테스트용. 200이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun depositSuccessfully(
            accountId: Long,
            amount: Long,
            memberId: Long,
        ): ApiResponse = deposit(accountId, amount, memberId).also { it.status shouldBe 200 }

        fun balanceOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun ledgerCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger", Int::class.java)!!

        fun Any?.asLong(): Long = (this as Number).toLong()

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
            accountIdPath: Any,
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/accounts/$accountIdPath/deposit"
        }

        @Suppress("UNCHECKED_CAST")
        fun ApiResponse.errorFields(): List<String> =
            (body["errors"].shouldNotBeNull() as List<Map<String, Any?>>).map { it["field"] as String }

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("POST /api/accounts/{id}/deposit") {
            context("내 계좌에 올바른 금액을 입금하면") {
                it("200과 계좌 id, 입금액, 입금 후 예수금을 반환한다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = deposit(accountId, 10_000, memberId)

                    response.status shouldBe 200
                    response.body["accountId"].asLong() shouldBe accountId
                    response.body["amount"].asLong() shouldBe 10_000
                    response.body["balance"].asLong() shouldBe 10_000 // 가입 직후 예수금 0에서 시작
                }
                it("응답 본문에는 accountId, amount, balance만 있다") {
                    val memberId = client.signup("Alice")

                    val response = depositSuccessfully(jdbcTemplate.accountIdOf(memberId), 10_000, memberId)

                    response.body.keys shouldContainExactlyInAnyOrder listOf("accountId", "amount", "balance")
                }
                it("계좌의 예수금이 입금액만큼 늘고, 묶인 금액은 그대로다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    depositSuccessfully(accountId, 10_000, memberId)

                    balanceOf(accountId) shouldBe 10_000
                    jdbcTemplate.queryForObject(
                        "SELECT hold_amount FROM account WHERE id = ?",
                        Long::class.java,
                        accountId,
                    ) shouldBe 0
                }
                it("원장에 DEPOSIT 항목 1건이 입금액과 입금 후 잔액으로 기록된다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    depositSuccessfully(accountId, 10_000, memberId)

                    val ledger = jdbcTemplate.queryForMap("SELECT account_id, amount, balance_after, type FROM ledger")
                    ledgerCount() shouldBe 1
                    ledger["account_id"].asLong() shouldBe accountId
                    ledger["amount"].asLong() shouldBe 10_000
                    ledger["balance_after"].asLong() shouldBe 10_000
                    ledger["type"] shouldBe "DEPOSIT"
                }
            }

            context("여러 번 입금하면") {
                it("예수금이 누적되고, 응답의 balance는 매번 누적된 예수금이다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    depositSuccessfully(accountId, 10_000, memberId).body["balance"].asLong() shouldBe 10_000
                    depositSuccessfully(accountId, 2_500, memberId).body["balance"].asLong() shouldBe 12_500
                    depositSuccessfully(accountId, 1, memberId).body["balance"].asLong() shouldBe 12_501
                }
                it("예수금은 원장 금액 합계와 같고, 마지막 원장의 입금 후 잔액과도 같다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    listOf(10_000L, 2_500L, 1L).forEach { depositSuccessfully(accountId, it, memberId) }

                    val ledgerSum =
                        jdbcTemplate.queryForObject(
                            "SELECT SUM(amount) FROM ledger WHERE account_id = ?",
                            Long::class.java,
                            accountId,
                        )
                    val lastBalanceAfter =
                        jdbcTemplate.queryForObject(
                            "SELECT balance_after FROM ledger WHERE account_id = ? ORDER BY id DESC LIMIT 1",
                            Long::class.java,
                            accountId,
                        )
                    ledgerCount() shouldBe 3
                    balanceOf(accountId) shouldBe 12_501
                    ledgerSum shouldBe balanceOf(accountId)
                    lastBalanceAfter shouldBe balanceOf(accountId)
                }
                it("1회 상한은 입금 1건 기준이라, 합계가 상한을 넘어도 입금된다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    depositSuccessfully(accountId, maxAmount, memberId)
                    val response = deposit(accountId, maxAmount, memberId)

                    response.status shouldBe 200
                    response.body["balance"].asLong() shouldBe maxAmount * 2
                }
            }

            context("금액이 허용 범위의 경계값이면") {
                withData(
                    nameFn = { "${it}센트는 입금된다" },
                    1L, // 최소 단위 1센트($0.01)
                    maxAmount, // 상한 $10,000
                ) { amount ->
                    resetTables() // withData 항목은 container로 등록돼 beforeEach가 돌지 않는다
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = deposit(accountId, amount, memberId)

                    response.status shouldBe 200
                    balanceOf(accountId) shouldBe amount
                }
            }

            context("금액이 허용 범위를 벗어나면") {
                withData(
                    nameFn = { "${it}이면 amount가 errors에 담긴 400 INVALID_INPUT을 응답하고, 예수금과 원장은 그대로다" },
                    0L,
                    -1L,
                    Long.MIN_VALUE, // 부호만 보고 거절해야 한다
                    maxAmount + 1, // 상한 + 1센트
                    Long.MAX_VALUE,
                ) { amount ->
                    resetTables()
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = deposit(accountId, amount, memberId)

                    response.shouldBeProblem(400, "INVALID_INPUT", accountId)
                    response.errorFields() shouldContain "amount"
                    balanceOf(accountId) shouldBe 0
                    ledgerCount() shouldBe 0
                }
            }

            context("amount가 빠졌거나 null이면") {
                it("빠진 amount가 errors에 담긴 400 INVALID_INPUT을 응답한다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = depositRaw(accountId, "{}", memberId.toString())

                    response.shouldBeProblem(400, "INVALID_INPUT", accountId)
                    response.errorFields() shouldContain "amount"
                }
                it("null인 amount가 errors에 담긴 400 INVALID_INPUT을 응답한다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = depositRaw(accountId, """{"amount": null}""", memberId.toString())

                    response.shouldBeProblem(400, "INVALID_INPUT", accountId)
                    response.errorFields() shouldContain "amount"
                }
            }

            // 숫자 형식 오류는 역직렬화 단계에서 실패한다. 어떤 예외로 오는지에 따라 errors가 없을 수 있어서 상태와 code만 본다
            context("amount가 센트 단위 정수가 아니면") {
                withData(
                    nameFn = { "amount: $it 이면 400 INVALID_INPUT을 응답하고, 예수금과 원장은 그대로다" },
                    "10.5", // 센트보다 작은 단위는 없다. 10센트로 잘려 입금되면 안 된다
                    "9223372036854775808", // Long.MAX_VALUE + 1. 파싱 예외가 500으로 새지 않아야 한다
                    "\"abc\"", // 숫자 아님
                ) { rawAmount ->
                    resetTables()
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = depositRaw(accountId, """{"amount": $rawAmount}""", memberId.toString())

                    response.shouldBeProblem(400, "INVALID_INPUT", accountId)
                    balanceOf(accountId) shouldBe 0
                    ledgerCount() shouldBe 0
                }
            }

            context("경로의 계좌 id가 숫자가 아니면") {
                it("400 INVALID_INPUT을 응답하고, 내 계좌의 예수금과 원장은 그대로다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = depositRaw("abc", """{"amount": 10000}""", memberId.toString())

                    response.shouldBeProblem(400, "INVALID_INPUT", "abc")
                    balanceOf(accountId) shouldBe 0
                    ledgerCount() shouldBe 0
                }
            }

            context("X-Member-Id 헤더가 없으면") {
                it("401 UNAUTHENTICATED를 응답하고, 예수금은 그대로다") {
                    val memberId = client.signup("Alice")
                    val accountId = jdbcTemplate.accountIdOf(memberId)

                    val response = depositRaw(accountId, """{"amount": 10000}""", null)

                    response.shouldBeProblem(401, "UNAUTHENTICATED", accountId)
                    balanceOf(accountId) shouldBe 0
                }
            }

            context("없는 계좌면") {
                it("404 ACCOUNT_NOT_FOUND를 응답한다") {
                    val memberId = client.signup("Alice")
                    val missingAccountId = jdbcTemplate.accountIdOf(memberId) + 1

                    val response = deposit(missingAccountId, 10_000, memberId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", missingAccountId)
                    ledgerCount() shouldBe 0
                }
            }

            context("남의 계좌면") {
                it("없는 계좌와 같은 404 ACCOUNT_NOT_FOUND를 응답하고, 그 계좌의 예수금과 원장은 그대로다") {
                    val aliceId = client.signup("Alice")
                    val bobId = client.signup("Bob")
                    val bobAccountId = jdbcTemplate.accountIdOf(bobId)

                    val response = deposit(bobAccountId, 10_000, aliceId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", bobAccountId)
                    balanceOf(bobAccountId) shouldBe 0
                    ledgerCount() shouldBe 0
                }
            }
        }
    })

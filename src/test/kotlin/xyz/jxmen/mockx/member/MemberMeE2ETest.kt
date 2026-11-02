package xyz.jxmen.mockx.member

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
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
import xyz.jxmen.mockx.ApiResponse
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.accountIdOf
import xyz.jxmen.mockx.signup

/**
 * 내 정보 조회 API 계약 테스트. 실제 서버(RANDOM_PORT)에 HTTP로 요청하고, Testcontainers MySQL을 쓴다.
 * - 회원은 가입 API로 만든다. 가입 직후 조회한 값이 가입 응답과 같은지로 member와 account 연결을 검증한다.
 * - 설정이 SignupE2ETest와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다. 프로파일을 붙이면 컨텍스트가 따로 뜬다.
 * - 계약은 docs/db-design-member.md 5장(인증), 6장(에러 응답)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class MemberMeE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다
        fun getMe(memberIdHeader: String?): ApiResponse =
            client
                .get()
                .uri("/api/members/me")
                .headers { headers -> memberIdHeader?.let { headers.set("X-Member-Id", it) } }
                .exchange { _, response ->
                    val body = response.bodyTo(String::class.java)
                    ApiResponse(
                        status = response.statusCode.value(),
                        headers = response.headers,
                        body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                    )
                }

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/members/me"
        }

        @Suppress("UNCHECKED_CAST")
        fun ApiResponse.accounts(): List<Map<String, Any?>> = body["accounts"] as List<Map<String, Any?>>

        beforeEach {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        describe("GET /api/members/me") {
            context("가입한 회원의 X-Member-Id면") {
                it("200과 회원 정보, 계좌번호를 반환한다") {
                    val memberId = client.signup(username = "Alice", nickname = "앨리스")

                    val response = getMe(memberId.toString())

                    response.status shouldBe 200
                    (response.body["memberId"] as Number).toLong() shouldBe memberId
                    response.body["username"] shouldBe "Alice" // 가입할 때 입력한 대소문자 그대로
                    response.body["nickname"] shouldBe "앨리스"

                    val account = response.accounts().single() // 가입하면 위탁계좌 하나가 개설된다
                    (account["id"] as Number).toLong() shouldBe jdbcTemplate.accountIdOf(memberId)
                    account["type"] shouldBe "BROKERAGE" // AccountType enum 이름 그대로
                    account["number"] shouldBe "100000000016" // 첫 순번(1)으로 발급된 계좌번호. 하이픈 없는 12자리 문자열
                    account["balance"] shouldBe 0 // 가입 직후 예수금은 0. 객체({"amount": 0})가 아니라 숫자여야 한다
                    account["holdAmount"] shouldBe 0
                }
                it("계좌의 예수금과 묶인 금액을 숫자로 반환한다") {
                    val memberId = client.signup(username = "Alice", nickname = "앨리스")
                    // 입금·주문 API가 아직 없어서 DB 값을 직접 바꾼다. 응답이 0으로 고정돼 있지 않은지 확인하려는 것이다
                    jdbcTemplate.update("UPDATE account SET balance = 30000, hold_amount = 5000 WHERE member_id = ?", memberId)

                    val response = getMe(memberId.toString())

                    response.status shouldBe 200
                    val account = response.accounts().single()
                    account["balance"] shouldBe 30_000
                    account["holdAmount"] shouldBe 5_000
                }
                it("응답 본문에는 memberId, username, nickname, accounts만 있고, 계좌에는 id, type, number, balance, holdAmount만 있다") {
                    val memberId = client.signup(username = "Alice", nickname = "앨리스")

                    val response = getMe(memberId.toString())

                    response.status shouldBe 200
                    response.body.keys shouldContainExactlyInAnyOrder listOf("memberId", "username", "nickname", "accounts")
                    response.accounts().single().keys shouldContainExactlyInAnyOrder
                        listOf("id", "type", "number", "balance", "holdAmount")
                }
            }

            context("회원이 여러 명이면") {
                it("헤더에 넣은 회원의 정보와 그 회원의 계좌만 반환한다") {
                    client.signup(username = "Alice", nickname = "앨리스")
                    val bobId = client.signup(username = "Bob", nickname = "바비")

                    val response = getMe(bobId.toString())

                    response.status shouldBe 200
                    (response.body["memberId"] as Number).toLong() shouldBe bobId
                    response.body["username"] shouldBe "Bob"
                    response.body["nickname"] shouldBe "바비"

                    val account = response.accounts().single() // Alice의 계좌가 섞이면 안 된다
                    (account["id"] as Number).toLong() shouldBe jdbcTemplate.accountIdOf(bobId)
                    account["number"] shouldBe "100000000024" // 두 번째 순번(2)은 Bob의 계좌
                }
            }

            context("X-Member-Id 헤더가 없으면") {
                it("401 UNAUTHENTICATED를 응답한다") {
                    getMe(null).shouldBeProblem(401, "UNAUTHENTICATED")
                }
            }

            // withData 항목은 container로 등록돼 beforeEach가 돌지 않는다. 헤더 검증은 DB 상태와 무관하므로 회원을 만들지 않는다
            context("X-Member-Id가 양의 정수가 아니면") {
                withData(
                    nameFn = { "'$it'이면 401 UNAUTHENTICATED를 응답한다" },
                    "", // 빈 값
                    "abc", // 숫자 아님
                    "1.5", // 소수
                    "9223372036854775808", // Long.MAX_VALUE + 1. 파싱 예외가 500으로 새지 않아야 한다
                    "0", // ID는 1부터 시작한다
                    "-1", // 음수
                ) { header ->
                    getMe(header).shouldBeProblem(401, "UNAUTHENTICATED")
                }
            }

            context("X-Member-Id에 해당하는 회원이 없으면") {
                it("404 MEMBER_NOT_FOUND를 응답한다") {
                    val memberId = client.signup(username = "Alice", nickname = "앨리스")

                    getMe((memberId + 1).toString()).shouldBeProblem(404, "MEMBER_NOT_FOUND")
                }
            }
        }
    })

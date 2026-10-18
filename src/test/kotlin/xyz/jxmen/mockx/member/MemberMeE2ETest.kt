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
import xyz.jxmen.mockx.TestcontainersConfiguration

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

        // 가입에 성공한 회원의 memberId를 돌려준다. 201이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun signup(
            username: String,
            nickname: String,
        ): Long {
            val status =
                client
                    .post()
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
            status.first shouldBe 201
            return (mapper.readValue<Map<String, Any?>>(status.second!!)["memberId"] as Number).toLong()
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

        // 계좌 id는 응답 말고는 알 방법이 없어서 DB에서 직접 꺼내 비교한다
        fun accountIdOf(memberId: Long): Long =
            jdbcTemplate.queryForObject("SELECT id FROM account WHERE member_id = ?", Long::class.java, memberId)!!

        beforeEach {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        describe("GET /api/members/me") {
            context("가입한 회원의 X-Member-Id면") {
                it("200과 회원 정보, 계좌번호를 반환한다") {
                    val memberId = signup(username = "Alice", nickname = "앨리스")

                    val response = getMe(memberId.toString())

                    response.status shouldBe 200
                    (response.body["memberId"] as Number).toLong() shouldBe memberId
                    response.body["username"] shouldBe "Alice" // 가입할 때 입력한 대소문자 그대로
                    response.body["nickname"] shouldBe "앨리스"

                    val account = response.accounts().single() // 가입하면 위탁계좌 하나가 개설된다
                    (account["id"] as Number).toLong() shouldBe accountIdOf(memberId)
                    account["type"] shouldBe "BROKERAGE" // AccountType enum 이름 그대로
                    account["number"] shouldBe "100000000016" // 첫 순번(1)으로 발급된 계좌번호. 하이픈 없는 12자리 문자열
                }
                it("응답 본문에는 memberId, username, nickname, accounts만 있고, 계좌에는 id, type, number만 있다") {
                    val memberId = signup(username = "Alice", nickname = "앨리스")

                    val response = getMe(memberId.toString())

                    response.status shouldBe 200
                    response.body.keys shouldContainExactlyInAnyOrder listOf("memberId", "username", "nickname", "accounts")
                    response.accounts().single().keys shouldContainExactlyInAnyOrder listOf("id", "type", "number")
                }
            }

            context("회원이 여러 명이면") {
                it("헤더에 넣은 회원의 정보와 그 회원의 계좌만 반환한다") {
                    signup(username = "Alice", nickname = "앨리스")
                    val bobId = signup(username = "Bob", nickname = "바비")

                    val response = getMe(bobId.toString())

                    response.status shouldBe 200
                    (response.body["memberId"] as Number).toLong() shouldBe bobId
                    response.body["username"] shouldBe "Bob"
                    response.body["nickname"] shouldBe "바비"

                    val account = response.accounts().single() // Alice의 계좌가 섞이면 안 된다
                    (account["id"] as Number).toLong() shouldBe accountIdOf(bobId)
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
                    val memberId = signup(username = "Alice", nickname = "앨리스")

                    getMe((memberId + 1).toString()).shouldBeProblem(404, "MEMBER_NOT_FOUND")
                }
            }
        }
    })

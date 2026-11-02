package xyz.jxmen.mockx.member

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBePositive
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import xyz.jxmen.mockx.ApiResponse
import xyz.jxmen.mockx.TestcontainersConfiguration

/**
 * 회원가입 API 계약 테스트. 실제 서버(RANDOM_PORT)에 HTTP로 요청하고, Testcontainers MySQL을 쓴다.
 * - 테스트 트랜잭션을 쓰지 않으므로 요청은 실제로 커밋된다. 대신 테스트마다 테이블을 비우고 순번을 초기화한다.
 * - 계약은 docs/db-design-member.md 4장(응답), 6장(에러 응답)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class SignupE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다
        fun postRaw(json: String): ApiResponse =
            client
                .post()
                .uri("/api/members")
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

        fun signup(body: Map<String, Any?>): ApiResponse = postRaw(mapper.writeValueAsString(body))

        // 가입 성공을 전제로 하는 테스트용. 201이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun signupSuccessfully(body: Map<String, Any?>): ApiResponse = signup(body).also { it.status shouldBe 201 }

        fun validRequest(
            username: String = "Alice",
            nickname: String = "앨리스",
            password: String = "pass1234",
            passwordConfirm: String = password,
        ): Map<String, Any?> =
            mapOf(
                "username" to username,
                "nickname" to nickname,
                "password" to password,
                "passwordConfirm" to passwordConfirm,
            )

        fun countRows(table: String): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/members"
        }

        @Suppress("UNCHECKED_CAST")
        fun ApiResponse.errorFields(): List<String> =
            (body["errors"].shouldNotBeNull() as List<Map<String, Any?>>).map { it["field"] as String }

        fun setSequence(value: Long) {
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = ? WHERE product_code = '100'", value)
        }

        fun currentSequence(): Long =
            jdbcTemplate.queryForObject(
                "SELECT next_value FROM account_number_sequence WHERE product_code = '100'",
                Long::class.java,
            )!!

        beforeEach {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            setSequence(0)
        }

        describe("POST /api/members") {
            context("올바른 요청이면") {
                it("201과 회원·계좌 정보를 반환하고, Location 헤더는 없다") {
                    val response = signup(validRequest(username = "Alice", nickname = "앨리스"))

                    response.status shouldBe 201
                    // 가리킬 조회 API(GET /api/members/{id})가 없으므로 Location을 주지 않는다
                    response.headers.location shouldBe null
                    (response.body["memberId"] as Number).toLong().shouldBePositive()
                    response.body["username"] shouldBe "Alice" // 입력한 대소문자 그대로
                    response.body["nickname"] shouldBe "앨리스"
                    // 첫 순번(1)으로 발급된 위탁계좌 번호. 하이픈 없이 숫자 12자리
                    response.body["accountNumber"] shouldBe "100000000016"
                }
                it("응답 본문에는 memberId, username, nickname, accountNumber만 있다") {
                    val response = signupSuccessfully(validRequest())

                    response.body.keys shouldContainExactlyInAnyOrder listOf("memberId", "username", "nickname", "accountNumber")
                }
                it("회원과 계좌가 1건씩 저장되고, 계좌는 가입한 회원의 것이다") {
                    val response = signupSuccessfully(validRequest())
                    val memberId = (response.body["memberId"] as Number).toLong()

                    countRows("member") shouldBe 1
                    countRows("account") shouldBe 1
                    jdbcTemplate.queryForObject("SELECT member_id FROM account", Long::class.java) shouldBe memberId
                }
                it("비밀번호는 BCrypt 해시로 저장된다") {
                    signupSuccessfully(validRequest(password = "pass1234"))

                    val stored = jdbcTemplate.queryForObject("SELECT password FROM member", String::class.java)!!
                    stored shouldNotBe "pass1234"
                    stored shouldStartWith "$2" // BCrypt 해시 접두어($2a$, $2b$, $2y$)
                }
                it("연속으로 가입하면 계좌번호 순번이 1씩 증가한다") {
                    signupSuccessfully(validRequest(username = "Alice"))

                    val response = signupSuccessfully(validRequest(username = "Bob"))

                    response.body["accountNumber"] shouldBe "100000000024"
                }
                it("정의되지 않은 필드는 무시하고 가입된다") {
                    val response = signup(validRequest() + ("role" to "ADMIN"))

                    response.status shouldBe 201
                }
            }

            context("이미 있는 username이면") {
                it("409 USERNAME_DUPLICATED를 응답하고, field는 username이다") {
                    signupSuccessfully(validRequest(username = "Alice"))

                    val response = signup(validRequest(username = "Alice"))

                    response.shouldBeProblem(409, "USERNAME_DUPLICATED")
                    response.body["field"] shouldBe "username"
                }
                it("대소문자만 달라도 409를 응답한다") {
                    signupSuccessfully(validRequest(username = "Alice"))

                    val response = signup(validRequest(username = "alice"))

                    response.shouldBeProblem(409, "USERNAME_DUPLICATED")
                }
                it("회원과 계좌가 추가로 저장되지 않는다") {
                    signupSuccessfully(validRequest(username = "Alice"))

                    signup(validRequest(username = "alice")).status shouldBe 409

                    countRows("member") shouldBe 1
                    countRows("account") shouldBe 1
                }
            }

            context("입력 형식이 틀리면") {
                it("400 INVALID_INPUT을 응답하고, errors에 틀린 필드가 담긴다") {
                    val response = signup(validRequest(username = "a"))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "username"
                    response.body.keys shouldNotContain "field"
                }
                it("여러 필드가 틀리면 errors에 모두 담긴다") {
                    val response = signup(validRequest(username = "a", nickname = "b"))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields().toSet() shouldBe setOf("username", "nickname")
                }
                it("회원과 계좌가 저장되지 않는다") {
                    signup(validRequest(username = "a")).status shouldBe 400

                    countRows("member") shouldBe 0
                    countRows("account") shouldBe 0
                }
            }

            context("password와 passwordConfirm이 다르면") {
                it("400 PASSWORD_NOT_MATCHED를 응답하고, field는 passwordConfirm이다") {
                    val response = signup(validRequest(password = "pass1234", passwordConfirm = "pass12345"))

                    response.shouldBeProblem(400, "PASSWORD_NOT_MATCHED")
                    response.body["field"] shouldBe "passwordConfirm"
                }
            }

            context("형식 오류와 비밀번호 불일치가 함께 있으면") {
                it("INVALID_INPUT을 응답한다") {
                    val response = signup(validRequest(username = "a", password = "pass1234", passwordConfirm = "pass12345"))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "username"
                }
            }

            context("필드가 빠졌거나 null이면") {
                it("빠진 필드가 errors에 담긴 400 INVALID_INPUT을 응답한다") {
                    val response = signup(validRequest() - "nickname")

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "nickname"
                }
                it("null인 필드가 errors에 담긴 400 INVALID_INPUT을 응답한다") {
                    val response = signup(validRequest() + ("username" to null))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "username"
                }
            }

            // 회원 저장, 순번 발급, 계좌 저장은 한 트랜잭션이다. 계좌 개설 단계에서 실패하면 셋 다 되돌아가야 한다.
            // 실패는 순번을 8자리 상한(99,999,999)에 맞춰 두고, 다음 순번이 AccountNumber.issue의 검증에 걸리게 해서 일으킨다
            context("회원 저장 뒤 계좌 개설이 실패하면") {
                it("회원과 계좌가 저장되지 않는다") {
                    setSequence(99_999_999)

                    signup(validRequest()).status shouldBe 500

                    countRows("member") shouldBe 0
                    countRows("account") shouldBe 0
                }
                it("계좌번호 순번도 증가하지 않는다") {
                    setSequence(99_999_999)

                    signup(validRequest()).status shouldBe 500

                    currentSequence() shouldBe 99_999_999
                }
                it("실패한 username으로 다시 가입할 수 있다") {
                    setSequence(99_999_999)
                    signup(validRequest(username = "Alice")).status shouldBe 500
                    setSequence(0)

                    val response = signup(validRequest(username = "Alice"))

                    response.status shouldBe 201
                }
            }

            context("JSON 문법이 깨졌으면") {
                it("400 INVALID_INPUT을 응답하고, errors와 field는 없다") {
                    val response = postRaw("""{"username": "Alice",""")

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.body.keys shouldNotContain "errors"
                    response.body.keys shouldNotContain "field"
                }
            }
        }
    })

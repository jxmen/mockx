package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import xyz.jxmen.mockx.fundAccount
import xyz.jxmen.mockx.newIdempotencyKey
import xyz.jxmen.mockx.signup

/**
 * 매수 주문 접수의 Idempotency-Key E2E 테스트. Testcontainers MySQL로 전체를 띄운다.
 * - 같은 (회원, 키)로 같은 내용을 다시 보내면 다시 처리하지 않고, 저장한 201에 Idempotent-Replayed: true를 붙여 돌려준다.
 * - 같은 키인데 정규화한 내용(지문)이 다르면 422 IDEMPOTENCY_KEY_REUSED다.
 * - 201만 저장한다. 실패(4xx)하면 키를 지우므로 같은 키로 다시 처리된다.
 * - 키 범위는 회원별이다. 다른 회원의 같은 키와 섞이지 않는다.
 * - 키는 테스트마다 새로 만든다. 캐시는 테이블을 비워도 남아서, 고정 키를 쓰면 앞 테스트의 응답이 돌아온다.
 * - 같은 키 동시 요청은 PlaceOrderIdempotencyConcurrencyTest가 맡는다.
 * - 계약은 docs/design/order.md 13장(테스트), 15장(Idempotency-Key)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class PlaceOrderIdempotencyE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        val replayedHeader = "Idempotent-Replayed"

        // $4,000. MU 10주 × $187.43 = 187,430을 두 번 받을 수 있는 금액이다.
        // 한 번만 받을 만큼이면 키 처리가 없어도 두 번째 요청이 422로 막혀 "주문 1건"이 우연히 통과한다
        val balance = 400_000L

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드, 헤더, 본문을 그대로 돌려받는다.
        // 본문은 문자열 그대로 보낸다. 필드 순서·대소문자만 다른 JSON을 보낼 때 쓴다
        fun placeOrderRaw(
            accountId: Long,
            memberId: Long,
            idempotencyKey: String,
            json: String,
        ): ApiResponse =
            client
                .post()
                .uri("/api/accounts/$accountId/orders")
                .header("X-Member-Id", memberId.toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.toByteArray(Charsets.UTF_8))
                .exchange { _, response ->
                    val body = response.bodyTo(String::class.java)
                    ApiResponse(
                        status = response.statusCode.value(),
                        headers = response.headers,
                        body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                    )
                }

        fun orderJson(
            symbol: String,
            price: Long,
            quantity: Long,
        ): String =
            mapper.writeValueAsString(
                mapOf("symbol" to symbol, "side" to "BUY", "price" to price, "quantity" to quantity),
            )

        fun placeOrder(
            accountId: Long,
            memberId: Long,
            idempotencyKey: String,
            symbol: String,
            price: Long,
            quantity: Long,
        ): ApiResponse = placeOrderRaw(accountId, memberId, idempotencyKey, orderJson(symbol, price, quantity))

        // 가입하고 예수금을 balance만큼 채운 계좌의 (memberId, accountId)를 돌려준다
        fun openAccount(
            username: String,
            balance: Long,
        ): Pair<Long, Long> {
            val memberId = client.signup(username)
            val accountId = jdbcTemplate.accountIdOf(memberId)
            client.fundAccount(accountId, memberId, balance)
            return memberId to accountId
        }

        fun balanceOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun holdOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT hold_amount FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

        fun orderCountOf(accountId: Long): Int =
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id = ?", Int::class.java, accountId)!!

        fun ledgerCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger", Int::class.java)!!

        fun Any?.asLong(): Long = (this as Number).toLong()

        fun ApiResponse.replayed(): String? = headers.getFirst(replayedHeader)

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
            accountId: Long,
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/accounts/$accountId/orders"
        }

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.execute("TRUNCATE TABLE orders")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("POST /api/accounts/{accountId}/orders") {
            context("같은 키로 같은 내용을 다시 보내면") {
                it("첫 응답과 같은 201 본문에 Idempotent-Replayed: true를 붙여 응답한다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val key = newIdempotencyKey()
                    val first = placeOrder(accountId, memberId, key, "MU", 18_743, 10)
                    first.status shouldBe 201

                    val second = placeOrder(accountId, memberId, key, "MU", 18_743, 10)

                    second.status shouldBe 201
                    second.body shouldBe first.body // orderId까지 같다
                    second.replayed() shouldBe "true"
                }
                it("첫 응답에는 Idempotent-Replayed 헤더가 없다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    val first = placeOrder(accountId, memberId, newIdempotencyKey(), "MU", 18_743, 10)

                    first.status shouldBe 201
                    first.replayed().shouldBeNull()
                }
                it("주문은 1건이고 hold는 한 번만 늘며, 예수금과 원장은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val key = newIdempotencyKey()

                    placeOrder(accountId, memberId, key, "MU", 18_743, 10).status shouldBe 201
                    placeOrder(accountId, memberId, key, "MU", 18_743, 10)

                    orderCount() shouldBe 1
                    holdOf(accountId) shouldBe 187_430 // 18,743 × 10. 두 번 처리됐다면 374,860
                    balanceOf(accountId) shouldBe balance
                    ledgerCount() shouldBe 1 // 예수금을 채운 입금 1건
                }
            }

            context("같은 키로 표기만 다른 같은 주문을 보내면") {
                // 지문은 본문 원문이 아니라 정규화한 값(symbol 대문자, side enum)으로 만든다 (docs/design/order.md 15장 "저장 형식")
                it("symbol·side 대소문자와 필드 순서가 달라도 재응답 201을 받고, 주문은 1건이다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val key = newIdempotencyKey()
                    val first = placeOrder(accountId, memberId, key, "MU", 18_743, 10)
                    first.status shouldBe 201

                    val second =
                        placeOrderRaw(
                            accountId,
                            memberId,
                            key,
                            """{"quantity":10,"price":18743,"side":"Buy","symbol":"mu"}""",
                        )

                    second.status shouldBe 201
                    second.replayed() shouldBe "true"
                    second.body["orderId"].asLong() shouldBe first.body["orderId"].asLong()
                    orderCount() shouldBe 1
                    holdOf(accountId) shouldBe 187_430
                }
            }

            context("같은 키로 가격만 다른 주문을 보내면") {
                it("422 IDEMPOTENCY_KEY_REUSED를 응답하고, 주문과 hold는 첫 주문 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val key = newIdempotencyKey()
                    placeOrder(accountId, memberId, key, "MU", 18_743, 10).status shouldBe 201

                    val response = placeOrder(accountId, memberId, key, "MU", 18_000, 10)

                    response.shouldBeProblem(422, "IDEMPOTENCY_KEY_REUSED", accountId)
                    orderCount() shouldBe 1
                    holdOf(accountId) shouldBe 187_430 // 첫 주문 18,743 × 10만 묶여 있다
                    balanceOf(accountId) shouldBe balance
                }
            }

            context("주문가능금액 부족(422)으로 실패한 키로 입금 후 다시 보내면") {
                // 실패하면 키를 지우므로 같은 키가 처음 보는 키로 처리된다. 지우지 않으면 409나 저장된 실패가 나온다
                it("재응답 헤더 없이 201로 새로 접수된다") {
                    val (memberId, accountId) = openAccount("Alice", 10_000) // $100
                    val key = newIdempotencyKey()
                    val failed = placeOrder(accountId, memberId, key, "MU", 18_743, 10) // 187,430 > 10,000
                    failed.shouldBeProblem(422, "INSUFFICIENT_AVAILABLE_AMOUNT", accountId)
                    orderCount() shouldBe 0

                    client.fundAccount(accountId, memberId, 200_000) // 예수금 10,000 + 200,000 = 210,000
                    val response = placeOrder(accountId, memberId, key, "MU", 18_743, 10)

                    response.status shouldBe 201
                    response.replayed().shouldBeNull()
                    orderCount() shouldBe 1
                    holdOf(accountId) shouldBe 187_430
                }
            }

            context("다른 회원이 같은 키를 쓰면") {
                // 키 범위는 (memberId, 키)다. 키만으로 찾으면 Bob이 Alice의 주문 응답을 받는다
                it("둘 다 재응답 헤더 없이 201로 접수되고, 계좌마다 주문과 hold가 따로 생긴다") {
                    val (aliceId, aliceAccountId) = openAccount("Alice", balance)
                    val (bobId, bobAccountId) = openAccount("Bob", balance)
                    val key = newIdempotencyKey()

                    val alice = placeOrder(aliceAccountId, aliceId, key, "MU", 18_743, 10)
                    val bob = placeOrder(bobAccountId, bobId, key, "MU", 18_743, 10)

                    alice.status shouldBe 201
                    bob.status shouldBe 201
                    alice.replayed().shouldBeNull()
                    bob.replayed().shouldBeNull()
                    bob.body["orderId"].asLong() shouldNotBe alice.body["orderId"].asLong()
                    orderCountOf(aliceAccountId) shouldBe 1
                    orderCountOf(bobAccountId) shouldBe 1
                    holdOf(aliceAccountId) shouldBe 187_430
                    holdOf(bobAccountId) shouldBe 187_430
                }
            }
        }
    })

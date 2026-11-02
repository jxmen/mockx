package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
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
import xyz.jxmen.mockx.fundAccount
import xyz.jxmen.mockx.ApiResponse
import xyz.jxmen.mockx.signup
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 매수 주문 취소 API E2E 테스트. Testcontainers MySQL로 전체를 띄운다.
 * - 취소하면 그 주문의 hold(price × quantity)만 풀리고, 예수금과 원장은 그대로다.
 * - 불변식: hold_amount = 그 계좌의 ACCEPTED 주문들의 price × quantity 합계 (docs/db-design-order.md 3장)
 * - 남의 주문은 없는 주문과 같은 404 ORDER_NOT_FOUND, 남의 계좌는 없는 계좌와 같은 404 ACCOUNT_NOT_FOUND다.
 * - 입력 검증(경로 변수 형식, 인증 헤더)은 CancelOrderControllerTest가 맡는다.
 * - 계약은 docs/db-design-order.md 4장(상태), 7장(처리 흐름), 10장(에러 응답)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class CancelOrderE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다
        fun cancelOrder(
            accountId: Long,
            orderId: Long,
            memberId: Long,
        ): ApiResponse =
            client
                .post()
                .uri("/api/accounts/$accountId/orders/$orderId/cancel")
                .header("X-Member-Id", memberId.toString())
                .exchange { _, response ->
                    val body = response.bodyTo(String::class.java)
                    ApiResponse(
                        status = response.statusCode.value(),
                        headers = response.headers,
                        body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                    )
                }

        // 취소 성공을 전제로 하는 테스트용. 200이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun cancelOrderSuccessfully(
            accountId: Long,
            orderId: Long,
            memberId: Long,
        ): ApiResponse = cancelOrder(accountId, orderId, memberId).also { it.status shouldBe 200 }

        // 응답 상태 코드만 돌려준다
        fun placeOrder(
            accountId: Long,
            memberId: Long,
            symbol: String,
            price: Long,
            quantity: Long,
        ): Int =
            client
                .post()
                .uri("/api/accounts/$accountId/orders")
                .header("X-Member-Id", memberId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("symbol" to symbol, "side" to "BUY", "price" to price, "quantity" to quantity))
                .exchange { _, response -> response.statusCode.value() }

        // 매수 주문을 접수하고 orderId를 돌려준다. 취소 테스트의 준비 작업이다
        fun placeOrderSuccessfully(
            accountId: Long,
            memberId: Long,
            symbol: String,
            price: Long,
            quantity: Long,
        ): Long {
            val (status, body) =
                client
                    .post()
                    .uri("/api/accounts/$accountId/orders")
                    .header("X-Member-Id", memberId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("symbol" to symbol, "side" to "BUY", "price" to price, "quantity" to quantity))
                    .exchange { _, response -> response.statusCode.value() to response.bodyTo(String::class.java) }
            status shouldBe 201
            return (mapper.readValue<Map<String, Any?>>(body!!)["orderId"] as Number).toLong()
        }

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

        fun ledgerCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger", Int::class.java)!!

        fun statusOf(orderId: Long): String =
            jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

        // canceled_at은 UTC로 저장된다(hibernate.jdbc.time_zone). JDBC 시간대 변환을 피하려고 문자열로 꺼내 Instant로 읽는다
        fun canceledAtOf(orderId: Long): Instant? =
            jdbcTemplate
                .queryForObject(
                    "SELECT DATE_FORMAT(canceled_at, '%Y-%m-%dT%H:%i:%s.%fZ') FROM orders WHERE id = ?",
                    String::class.java,
                    orderId,
                )?.let { Instant.parse(it) }

        // hold 불변식의 오른쪽. 그 계좌의 ACCEPTED 주문들의 price × quantity 합계
        fun acceptedOrderAmountOf(accountId: Long): Long =
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(price * quantity), 0) FROM orders WHERE account_id = ? AND status = 'ACCEPTED'",
                Long::class.java,
                accountId,
            )!!

        fun Any?.asLong(): Long = (this as Number).toLong()

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
            accountId: Long,
            orderId: Long,
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/accounts/$accountId/orders/$orderId/cancel"
        }

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.execute("TRUNCATE TABLE orders")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        // 공통 준비: 예수금 $1,000에 ACCEPTED 주문 2건.
        // 주문이 하나뿐이면 hold를 두 번 푸는 버그가 Account.release의 require(500)에 가려진다. 다른 주문의 hold를 깔아 둔다
        val balance = 100_000L // $1,000
        val muAmount = 37_486L // MU 18,743 × 2
        val spyAmount = 60_012L // SPY 60,012 × 1

        data class Fixture(
            val memberId: Long,
            val accountId: Long,
            val muOrderId: Long,
            val spyOrderId: Long,
        )

        fun openAccountWithTwoOrders(username: String): Fixture {
            val (memberId, accountId) = openAccount(username, balance)
            val muOrderId = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 2)
            val spyOrderId = placeOrderSuccessfully(accountId, memberId, "SPY", 60_012, 1)
            holdOf(accountId) shouldBe muAmount + spyAmount // 97,498
            return Fixture(memberId, accountId, muOrderId, spyOrderId)
        }

        // 취소가 일어나지 않았는지 확인한다. 두 주문 모두 ACCEPTED이고 hold와 예수금은 준비한 그대로다
        fun Fixture.shouldBeUntouched() {
            statusOf(muOrderId) shouldBe "ACCEPTED"
            statusOf(spyOrderId) shouldBe "ACCEPTED"
            canceledAtOf(muOrderId).shouldBeNull()
            holdOf(accountId) shouldBe muAmount + spyAmount
            balanceOf(accountId) shouldBe balance
        }

        beforeEach { resetTables() }

        describe("POST /api/accounts/{accountId}/orders/{orderId}/cancel") {
            context("ACCEPTED 주문을 취소하면") {
                it("200과 취소된 주문의 orderId, symbol, side, price, quantity, status, canceledAt을 반환한다") {
                    val f = openAccountWithTwoOrders("Alice")

                    val response = cancelOrder(f.accountId, f.muOrderId, f.memberId)

                    response.status shouldBe 200
                    response.body.keys shouldContainExactlyInAnyOrder
                        listOf("orderId", "symbol", "side", "price", "quantity", "status", "canceledAt")
                    response.body["orderId"].asLong() shouldBe f.muOrderId
                    response.body["symbol"] shouldBe "MU"
                    response.body["side"] shouldBe "BUY"
                    response.body["price"].asLong() shouldBe 18_743
                    response.body["quantity"].asLong() shouldBe 2
                    response.body["status"] shouldBe "CANCELED"
                    // 응답 시각은 DB에 저장된 취소 시각과 같다
                    Instant.parse(response.body["canceledAt"] as String) shouldBe canceledAtOf(f.muOrderId)
                }
                it("주문이 CANCELED로 바뀌고 취소 시각이 기록된다") {
                    val f = openAccountWithTwoOrders("Alice")
                    // canceled_at은 마이크로초로 잘려 저장되므로 비교 기준도 자른다
                    val before = Instant.now().truncatedTo(ChronoUnit.MICROS)

                    cancelOrderSuccessfully(f.accountId, f.muOrderId, f.memberId)

                    val after = Instant.now()
                    statusOf(f.muOrderId) shouldBe "CANCELED"
                    val canceledAt = canceledAtOf(f.muOrderId).shouldNotBeNull()
                    canceledAt shouldBeGreaterThanOrEqualTo before
                    canceledAt shouldBeLessThanOrEqualTo after
                }
                it("그 주문의 hold만 풀리고, 다른 주문과 예수금, 원장은 그대로다") {
                    val f = openAccountWithTwoOrders("Alice")

                    cancelOrderSuccessfully(f.accountId, f.muOrderId, f.memberId)

                    holdOf(f.accountId) shouldBe spyAmount // 97,498 - 37,486 = 60,012
                    holdOf(f.accountId) shouldBe acceptedOrderAmountOf(f.accountId)
                    statusOf(f.spyOrderId) shouldBe "ACCEPTED"
                    balanceOf(f.accountId) shouldBe balance
                    ledgerCount() shouldBe 1 // 예수금을 채운 입금 1건. release는 원장에 남지 않는다
                }
            }

            context("취소로 풀린 금액이면") {
                it("그 금액으로 다시 매수할 수 있다") {
                    val (memberId, accountId) = openAccount("Alice", 10_000) // $100
                    val orderId = placeOrderSuccessfully(accountId, memberId, "MU", 8_000, 1) // 주문가능금액 2,000
                    placeOrder(accountId, memberId, "MU", 8_000, 1) shouldBe 422 // 취소 전에는 모자란다

                    cancelOrderSuccessfully(accountId, orderId, memberId)

                    placeOrder(accountId, memberId, "MU", 8_000, 1) shouldBe 201
                    holdOf(accountId) shouldBe 8_000
                    holdOf(accountId) shouldBe acceptedOrderAmountOf(accountId)
                }
            }

            context("이미 취소된 주문이면") {
                it("409 ORDER_NOT_CANCELABLE을 응답하고, 다른 주문의 hold와 첫 취소 시각은 그대로다") {
                    val f = openAccountWithTwoOrders("Alice")
                    cancelOrderSuccessfully(f.accountId, f.muOrderId, f.memberId)
                    val firstCanceledAt = canceledAtOf(f.muOrderId)

                    val response = cancelOrder(f.accountId, f.muOrderId, f.memberId)

                    response.shouldBeProblem(409, "ORDER_NOT_CANCELABLE", f.accountId, f.muOrderId)
                    // 상태 확인 없이 다시 풀면 SPY 몫까지 풀려 0이 된다
                    holdOf(f.accountId) shouldBe spyAmount
                    holdOf(f.accountId) shouldBe acceptedOrderAmountOf(f.accountId)
                    statusOf(f.spyOrderId) shouldBe "ACCEPTED"
                    canceledAtOf(f.muOrderId) shouldBe firstCanceledAt
                }
            }

            context("주문이 하나뿐인 계좌에서 취소된 주문을 다시 취소하면") {
                // 다른 주문의 hold가 없어서, 상태 검사보다 release가 먼저면 hold 0에서 풀려다 500이 난다
                it("409 ORDER_NOT_CANCELABLE을 응답하고, hold는 0 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", 10_000) // $100
                    val orderId = placeOrderSuccessfully(accountId, memberId, "MU", 8_000, 1)
                    cancelOrderSuccessfully(accountId, orderId, memberId)
                    val firstCanceledAt = canceledAtOf(orderId)

                    val response = cancelOrder(accountId, orderId, memberId)

                    response.shouldBeProblem(409, "ORDER_NOT_CANCELABLE", accountId, orderId)
                    holdOf(accountId) shouldBe 0
                    canceledAtOf(orderId) shouldBe firstCanceledAt
                    balanceOf(accountId) shouldBe 10_000
                }
            }

            context("내 계좌에 없는 주문이면") {
                it("404 ORDER_NOT_FOUND를 응답하고, 주문과 hold는 그대로다") {
                    val f = openAccountWithTwoOrders("Alice")
                    val missingOrderId = f.spyOrderId + 1

                    val response = cancelOrder(f.accountId, missingOrderId, f.memberId)

                    response.shouldBeProblem(404, "ORDER_NOT_FOUND", f.accountId, missingOrderId)
                    f.shouldBeUntouched()
                }
            }

            context("내 계좌 경로에 남의 주문 id를 넣으면") {
                it("없는 주문과 같은 404 ORDER_NOT_FOUND를 응답하고, 남의 주문과 내 hold는 그대로다") {
                    // Alice에게도 hold를 깔아 둔다. 주문 id로만 찾는 구현이면 Alice의 hold에서 Bob 주문 금액이 풀려 버린다
                    val alice = openAccountWithTwoOrders("Alice")
                    val (bobId, bobAccountId) = openAccount("Bob", balance)
                    val bobOrderId = placeOrderSuccessfully(bobAccountId, bobId, "MU", 18_743, 1)

                    val response = cancelOrder(alice.accountId, bobOrderId, alice.memberId)

                    response.shouldBeProblem(404, "ORDER_NOT_FOUND", alice.accountId, bobOrderId)
                    statusOf(bobOrderId) shouldBe "ACCEPTED"
                    canceledAtOf(bobOrderId).shouldBeNull()
                    holdOf(bobAccountId) shouldBe 18_743
                    alice.shouldBeUntouched()
                }
            }

            context("hold가 없는 내 계좌 경로에 남의 주문 id를 넣으면") {
                // 소유권 검사보다 release가 먼저면 hold 0에서 풀려다 500이 난다.
                // 없는 id는 404, 있는 남의 id는 500이 되어 응답 코드로 주문이 있는지 구분된다
                it("없는 주문과 같은 404 ORDER_NOT_FOUND를 응답하고, 남의 주문과 hold는 그대로다") {
                    val (aliceId, aliceAccountId) = openAccount("Alice", 10_000) // 주문 없음, hold 0
                    val (bobId, bobAccountId) = openAccount("Bob", balance)
                    val bobOrderId = placeOrderSuccessfully(bobAccountId, bobId, "MU", 18_743, 1)

                    val response = cancelOrder(aliceAccountId, bobOrderId, aliceId)

                    response.shouldBeProblem(404, "ORDER_NOT_FOUND", aliceAccountId, bobOrderId)
                    holdOf(aliceAccountId) shouldBe 0
                    statusOf(bobOrderId) shouldBe "ACCEPTED"
                    holdOf(bobAccountId) shouldBe 18_743
                }
            }

            context("내 계좌 경로에 남의 취소된 주문 id를 넣으면") {
                // 상태를 소유권보다 먼저 보면 409가 나가서, 그 주문 id가 실제로 있다는 것을 알려 주게 된다
                it("409가 아니라 없는 주문과 같은 404 ORDER_NOT_FOUND를 응답한다") {
                    val alice = openAccountWithTwoOrders("Alice")
                    val (bobId, bobAccountId) = openAccount("Bob", balance)
                    val bobOrderId = placeOrderSuccessfully(bobAccountId, bobId, "MU", 18_743, 1)
                    cancelOrderSuccessfully(bobAccountId, bobOrderId, bobId)

                    val response = cancelOrder(alice.accountId, bobOrderId, alice.memberId)

                    response.shouldBeProblem(404, "ORDER_NOT_FOUND", alice.accountId, bobOrderId)
                    alice.shouldBeUntouched()
                }
            }

            context("남의 계좌면") {
                it("없는 계좌와 같은 404 ACCOUNT_NOT_FOUND를 응답하고, 그 계좌의 주문과 hold는 그대로다") {
                    val (aliceId, _) = openAccount("Alice", 0)
                    val bob = openAccountWithTwoOrders("Bob")

                    val response = cancelOrder(bob.accountId, bob.muOrderId, aliceId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", bob.accountId, bob.muOrderId)
                    bob.shouldBeUntouched()
                }
            }

            context("없는 계좌면") {
                it("404 ACCOUNT_NOT_FOUND를 응답하고, 주문과 hold는 그대로다") {
                    val f = openAccountWithTwoOrders("Alice")
                    val missingAccountId = f.accountId + 1

                    val response = cancelOrder(missingAccountId, f.muOrderId, f.memberId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", missingAccountId, f.muOrderId)
                    f.shouldBeUntouched()
                }
            }
        }
    })

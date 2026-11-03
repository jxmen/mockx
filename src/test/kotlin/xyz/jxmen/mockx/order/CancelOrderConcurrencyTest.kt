package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
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
import xyz.jxmen.mockx.newIdempotencyKey
import xyz.jxmen.mockx.signup
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 매수 주문 취소 동시성 테스트. 같은 주문에 취소 요청을 HTTP로 동시에 보내고, Testcontainers MySQL을 쓴다.
 * - 1건만 취소되고 나머지는 409 ORDER_NOT_CANCELABLE이어야 한다. hold는 한 번만 풀린다.
 * - 계좌에 다른 ACCEPTED 주문을 깔아 둔다. hold가 두 번 풀리면 그 주문의 몫이 사라져 불변식이 깨진다.
 *   주문이 하나뿐이면 두 번째 release가 Account.release의 require로 500이 나서 버그가 가려진다.
 * - 계좌 락을 잡기 전에 주문을 읽으면, 락을 기다린 요청이 예전 상태(ACCEPTED)를 보고 다시 풀 수 있다.
 *   (docs/db-design-order.md 9장 "락을 잡은 뒤에 읽는 값". 그 장의 "확인 필요"를 이 테스트로 확인한다)
 * - 락 순서가 틀려도 실행할 때마다 결과가 달라질 수 있다. 한 번 통과했다고 안전한 것은 아니다.
 * - 계약은 docs/db-design-order.md 3장(hold 불변식), 7장(처리 흐름), 9장(동시성 제어), 13장(테스트)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class CancelOrderConcurrencyTest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 요청마다 스레드를 하나씩 둬서 모두 출발선에 세운다
        val requestCount = 10

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

        // 매수 주문을 접수하고 orderId를 돌려준다
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
                    .header("Idempotency-Key", newIdempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("symbol" to symbol, "side" to "BUY", "price" to price, "quantity" to quantity))
                    .exchange { _, response -> response.statusCode.value() to response.bodyTo(String::class.java) }
            status shouldBe 201
            return (mapper.readValue<Map<String, Any?>>(body!!)["orderId"] as Number).toLong()
        }

        // 응답 상태 코드만 돌려준다. 4xx, 5xx에도 예외를 던지지 않는다
        fun cancelOrder(
            accountId: Long,
            orderId: Long,
            memberId: Long,
        ): Int =
            client
                .post()
                .uri("/api/accounts/$accountId/orders/$orderId/cancel")
                .header("X-Member-Id", memberId.toString())
                .exchange { _, response -> response.statusCode.value() }

        // 모든 스레드를 출발선에 세운 뒤 한꺼번에 출발시킨다. 상태 코드별 응답 건수를 돌려준다
        fun cancelOrdersConcurrently(
            accountId: Long,
            orderId: Long,
            memberId: Long,
        ): Map<Int, Int> {
            val ready = CountDownLatch(requestCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(requestCount)
            val statuses = ConcurrentLinkedQueue<Int>()
            val failures = ConcurrentLinkedQueue<Throwable>()

            Executors.newFixedThreadPool(requestCount).use { executor ->
                repeat(requestCount) {
                    executor.submit {
                        try {
                            ready.countDown()
                            start.await()
                            statuses.add(cancelOrder(accountId, orderId, memberId))
                        } catch (e: Throwable) {
                            failures.add(e)
                        } finally {
                            done.countDown()
                        }
                    }
                }
                ready.await(10, TimeUnit.SECONDS) shouldBe true
                start.countDown()
                done.await(60, TimeUnit.SECONDS) shouldBe true
            }
            failures.shouldBeEmpty() // 연결 오류 같은 요청 자체의 실패. 이게 있으면 상태 코드를 셀 수 없다
            return statuses.groupingBy { it }.eachCount()
        }

        fun balanceOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun holdOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT hold_amount FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun statusOf(orderId: Long): String =
            jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

        // hold 불변식의 오른쪽. 그 계좌의 ACCEPTED 주문들의 price × quantity 합계
        fun acceptedOrderAmountOf(accountId: Long): Long =
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(price * quantity), 0) FROM orders WHERE account_id = ? AND status = 'ACCEPTED'",
                Long::class.java,
                accountId,
            )!!

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.execute("TRUNCATE TABLE orders")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("POST /api/accounts/{accountId}/orders/{orderId}/cancel") {
            context("ACCEPTED 주문 2건이 있는 계좌에서 같은 주문에 취소 10건을 동시에 요청하면") {
                val balance = 100_000L // $1,000
                val spyAmount = 60_012L // SPY 60,012 × 1

                it("1건만 200으로 취소되고, 나머지 9건은 409다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val muOrderId = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 2)
                    placeOrderSuccessfully(accountId, memberId, "SPY", 60_012, 1)

                    val statusCounts = cancelOrdersConcurrently(accountId, muOrderId, memberId)

                    // 500 같은 예상 밖의 응답이 섞이면 이 비교에서 드러난다
                    statusCounts shouldBe mapOf(200 to 1, 409 to requestCount - 1)
                    statusOf(muOrderId) shouldBe "CANCELED"
                }
                it("hold는 한 번만 풀려 남은 ACCEPTED 주문 합계와 같고, 예수금은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", balance)
                    val muOrderId = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 2)
                    val spyOrderId = placeOrderSuccessfully(accountId, memberId, "SPY", 60_012, 1)

                    cancelOrdersConcurrently(accountId, muOrderId, memberId)

                    holdOf(accountId) shouldBe spyAmount // 97,498 - 37,486 = 60,012. 두 번 풀리면 22,526 이하가 된다
                    holdOf(accountId) shouldBe acceptedOrderAmountOf(accountId)
                    statusOf(spyOrderId) shouldBe "ACCEPTED"
                    balanceOf(accountId) shouldBe balance
                }
            }
        }
    })

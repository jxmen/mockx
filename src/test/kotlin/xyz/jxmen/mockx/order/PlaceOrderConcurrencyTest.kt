package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
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
 * 매수 주문 동시성 테스트. 같은 계좌에 매수 주문을 HTTP로 동시에 보내고, Testcontainers MySQL을 쓴다.
 * - 같은 계좌에 주문이 몰려도 hold 갱신 손실(lost update) 없이, 주문가능금액 안에서만 접수되어야 한다.
 * - 불변식: 접수된 주문의 금액 합계 = hold_amount ≤ balance. 나머지 요청은 422 INSUFFICIENT_AVAILABLE_AMOUNT다.
 * - PlaceOrderService가 아직 없어서 DepositConcurrencyTest처럼 서비스를 직접 부르지 않고 HTTP로 요청한다.
 *   요청은 Tomcat 스레드를 거쳐 커넥션 풀(기본 10)에서 커넥션을 얻은 만큼만 동시에 DB에 닿는다.
 * - 설정이 DepositE2ETest와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다.
 * - 락 없이 구현하면 실행할 때마다 결과가 달라질 수 있다. 한 번 통과했다고 안전한 것은 아니다.
 * - 계약은 docs/design/order.md 3장(hold 불변식), 9장(동시성 제어), 13장(테스트)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class PlaceOrderConcurrencyTest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 요청마다 스레드를 하나씩 둬서 모두 출발선에 세운다
        val requestCount = 100

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

        // 응답 상태 코드만 돌려준다. 4xx, 5xx에도 예외를 던지지 않는다
        fun placeOrder(
            accountId: Long,
            memberId: Long,
            price: Long,
            quantity: Long,
        ): Int =
            client
                .post()
                .uri("/api/accounts/$accountId/orders")
                .header("X-Member-Id", memberId.toString())
                .header("Idempotency-Key", newIdempotencyKey()) // 요청마다 다른 주문이므로 키도 다르다
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("symbol" to "MU", "side" to "BUY", "price" to price, "quantity" to quantity))
                .exchange { _, response -> response.statusCode.value() }

        // 모든 스레드를 출발선에 세운 뒤 한꺼번에 출발시킨다. 상태 코드별 응답 건수를 돌려준다
        fun placeOrdersConcurrently(
            accountId: Long,
            memberId: Long,
            price: Long,
            quantity: Long,
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
                            statuses.add(placeOrder(accountId, memberId, price, quantity))
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

        describe("POST /api/accounts/{accountId}/orders") {
            context("예수금 $100인 계좌에 $3 매수 100건을 동시에 요청하면") {
                // 10,000 / 300 = 33.3 → 33건(9,900)까지 접수되고, 34건째(10,200)부터 주문가능금액을 넘는다
                val balance = 10_000L
                val price = 300L
                val acceptableCount = 33

                it("33건만 201로 접수되고, 나머지 67건은 422다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    val statusCounts = placeOrdersConcurrently(accountId, memberId, price, 1)

                    // 500 같은 예상 밖의 응답이 섞이면 이 비교에서 드러난다
                    statusCounts shouldBe mapOf(201 to acceptableCount, 422 to requestCount - acceptableCount)
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java) shouldBe acceptableCount
                }
                it("hold 금액은 ACCEPTED 주문 합계와 같고 예수금을 넘지 않으며, 예수금은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    placeOrdersConcurrently(accountId, memberId, price, 1)

                    holdOf(accountId) shouldBe price * acceptableCount // 300 × 33 = 9,900
                    holdOf(accountId) shouldBe acceptedOrderAmountOf(accountId)
                    holdOf(accountId) shouldBeLessThanOrEqual balanceOf(accountId)
                    balanceOf(accountId) shouldBe balance
                }
            }
        }
    })

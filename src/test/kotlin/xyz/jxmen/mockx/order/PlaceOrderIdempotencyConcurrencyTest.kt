package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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
 * 같은 Idempotency-Key 동시 요청 테스트. 같은 키·같은 내용의 매수 주문을 HTTP로 동시에 보내고, Testcontainers MySQL을 쓴다.
 * - 처음 등록한 요청 하나만 처리되고, 나머지는 처리 중이면 409 IDEMPOTENCY_KEY_IN_PROGRESS, 끝난 뒤면 재응답 201이다.
 * - 409와 재응답의 비율은 실행마다 다르다. 그래서 "새로 처리된 201이 1건"과 "주문 1건, hold 1번"으로 확인한다.
 * - 예수금은 100건을 모두 받을 만큼 채운다. 1건만 받을 만큼이면 키 처리가 없어도 주문이 1건이라 우연히 통과한다.
 * - "확인 후 등록"이 원자적이지 않으면 실행할 때마다 결과가 달라질 수 있다. 한 번 통과했다고 안전한 것은 아니다.
 * - 계약은 docs/design/order.md 13장(테스트), 15장(처리 흐름)을 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class PlaceOrderIdempotencyConcurrencyTest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 요청마다 스레드를 하나씩 둬서 모두 출발선에 세운다
        val requestCount = 100

        // MU 1주 × $1.00. 100건을 모두 접수해도 10,000센트라 예수금 200,000 안에 든다
        val balance = 200_000L
        val price = 100L

        data class Result(
            val status: Int,
            val replayed: Boolean,
            val orderId: Long?,
            val code: String?,
        ) {
            val isFresh201 get() = status == 201 && !replayed
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

        // 4xx, 5xx에도 예외를 던지지 않는다
        fun placeOrder(
            accountId: Long,
            memberId: Long,
            idempotencyKey: String,
        ): Result =
            client
                .post()
                .uri("/api/accounts/$accountId/orders")
                .header("X-Member-Id", memberId.toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("symbol" to "MU", "side" to "BUY", "price" to price, "quantity" to 1))
                .exchange { _, response ->
                    val text = response.bodyTo(String::class.java)
                    val body: Map<String, Any?> = if (text.isNullOrBlank()) emptyMap() else mapper.readValue(text)
                    Result(
                        status = response.statusCode.value(),
                        replayed = response.headers.getFirst("Idempotent-Replayed") == "true",
                        orderId = (body["orderId"] as? Number)?.toLong(),
                        code = body["code"] as? String,
                    )
                }

        // 모든 스레드를 출발선에 세운 뒤 같은 키로 한꺼번에 출발시킨다
        fun placeOrdersConcurrently(
            accountId: Long,
            memberId: Long,
            idempotencyKey: String,
        ): List<Result> {
            val ready = CountDownLatch(requestCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(requestCount)
            val results = ConcurrentLinkedQueue<Result>()
            val failures = ConcurrentLinkedQueue<Throwable>()

            Executors.newFixedThreadPool(requestCount).use { executor ->
                repeat(requestCount) {
                    executor.submit {
                        try {
                            ready.countDown()
                            start.await()
                            results.add(placeOrder(accountId, memberId, idempotencyKey))
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
            failures.shouldBeEmpty() // 연결 오류 같은 요청 자체의 실패. 이게 있으면 응답을 셀 수 없다
            return results.toList()
        }

        fun balanceOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun holdOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT hold_amount FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.execute("TRUNCATE TABLE orders")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("POST /api/accounts/{accountId}/orders") {
            context("같은 키로 같은 주문 100건을 동시에 보내면") {
                it("새로 처리된 201은 1건이고, 나머지는 409 IDEMPOTENCY_KEY_IN_PROGRESS 또는 재응답 201이다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    val results = placeOrdersConcurrently(accountId, memberId, newIdempotencyKey())

                    results.filter { it.isFresh201 } shouldHaveSize 1
                    // 500이나 422처럼 예상 밖의 응답이 섞이면 여기서 드러난다
                    results.filterNot { it.isFresh201 || (it.status == 201 && it.replayed) || it.status == 409 }.shouldBeEmpty()
                    results.filter { it.status == 409 && it.code != "IDEMPOTENCY_KEY_IN_PROGRESS" }.shouldBeEmpty()
                }
                it("모든 201 응답의 orderId가 같다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    val results = placeOrdersConcurrently(accountId, memberId, newIdempotencyKey())

                    results.filter { it.status == 201 }.map { it.orderId }.toSet() shouldHaveSize 1
                }
                it("주문은 1건이고 hold는 한 번만 늘며, 예수금은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", balance)

                    placeOrdersConcurrently(accountId, memberId, newIdempotencyKey())

                    orderCount() shouldBe 1
                    holdOf(accountId) shouldBe price // 100 × 1. 키 처리가 없으면 100 × 100 = 10,000
                    balanceOf(accountId) shouldBe balance
                }
            }
        }
    })

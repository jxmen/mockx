package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
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

/**
 * 내 주문 목록 조회 API E2E 테스트. Testcontainers MySQL로 전체를 띄운다.
 * - 그 계좌의 모든 상태의 주문을 최신순(orderId 내림차순)으로 orders 배열에 담아 응답한다. 주문이 없으면 빈 배열이다.
 * - side, status 필터는 쉼표로 여러 값을 받고, 둘을 함께 주면 두 조건을 모두 만족하는 주문만 남는다(AND).
 * - 남의 계좌는 없는 계좌와 같은 404 ACCOUNT_NOT_FOUND다.
 * - 주문이 늘어도 쿼리 수가 늘지 않는다(N+1 없음). Hibernate 통계로 요청 하나에서 실행된 JDBC statement 수를 센다.
 * - 필터 값 형식, 경로 변수 형식, 인증 헤더는 GetOrdersControllerTest가 맡는다.
 * - 계약은 docs/db-design-order.md 8장(목록 조회), 10장(에러 응답)을 따른다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // N+1 검증용 쿼리 수 집계. 켜면 세션마다 찍히는 통계 로그는 끈다
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
    ],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class GetOrdersE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
    entityManagerFactory: EntityManagerFactory,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

        // query는 "?status=ACCEPTED"처럼 물음표부터 그대로 붙인다.
        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다
        fun getOrders(
            accountId: Long,
            memberId: Long,
            query: String = "",
        ): ApiResponse =
            client
                .get()
                .uri("/api/accounts/$accountId/orders$query")
                .header("X-Member-Id", memberId.toString())
                .exchange { _, response ->
                    val body = response.bodyTo(String::class.java)
                    ApiResponse(
                        status = response.statusCode.value(),
                        headers = response.headers,
                        body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                    )
                }

        @Suppress("UNCHECKED_CAST")
        fun ApiResponse.orders(): List<Map<String, Any?>> = body["orders"].shouldNotBeNull() as List<Map<String, Any?>>

        // 조회 성공을 전제로 하는 테스트용. 200이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun getOrdersSuccessfully(
            accountId: Long,
            memberId: Long,
            query: String = "",
        ): List<Map<String, Any?>> = getOrders(accountId, memberId, query).also { it.status shouldBe 200 }.orders()

        fun Any?.asLong(): Long = (this as Number).toLong()

        fun List<Map<String, Any?>>.orderIds(): List<Long> = map { it["orderId"].asLong() }

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
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("symbol" to symbol, "side" to "BUY", "price" to price, "quantity" to quantity))
                    .exchange { _, response -> response.statusCode.value() to response.bodyTo(String::class.java) }
            status shouldBe 201
            return (mapper.readValue<Map<String, Any?>>(body!!)["orderId"] as Number).toLong()
        }

        fun cancelOrderSuccessfully(
            accountId: Long,
            orderId: Long,
            memberId: Long,
        ) {
            val status =
                client
                    .post()
                    .uri("/api/accounts/$accountId/orders/$orderId/cancel")
                    .header("X-Member-Id", memberId.toString())
                    .exchange { _, response -> response.statusCode.value() }
            status shouldBe 200
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

        // 시각은 UTC로 저장된다(hibernate.jdbc.time_zone). JDBC 시간대 변환을 피하려고 문자열로 꺼내 Instant로 읽는다
        fun instantColumnOf(
            column: String,
            orderId: Long,
        ): Instant? =
            jdbcTemplate
                .queryForObject(
                    "SELECT DATE_FORMAT($column, '%Y-%m-%dT%H:%i:%s.%fZ') FROM orders WHERE id = ?",
                    String::class.java,
                    orderId,
                )?.let { Instant.parse(it) }

        fun createdAtOf(orderId: Long): Instant = instantColumnOf("created_at", orderId).shouldNotBeNull()

        fun canceledAtOf(orderId: Long): Instant? = instantColumnOf("canceled_at", orderId)

        // block 안에서 실행된 JDBC statement 수. 통계는 SessionFactory 전체 값이라 block 앞에서 비운다
        fun statementCountOf(block: () -> Unit): Long {
            statistics.clear()
            block()
            return statistics.prepareStatementCount
        }

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

        // 공통 준비: MU, SPY, SGOV 순서로 매수하고 MU만 취소한다.
        // 최신순이면 SGOV(ACCEPTED) → SPY(ACCEPTED) → MU(CANCELED)
        val balance = 200_000L // $2,000. hold 합계 37,486 + 60,012 + 10,048 = 107,546보다 크다

        data class Fixture(
            val memberId: Long,
            val accountId: Long,
            val muOrderId: Long,
            val spyOrderId: Long,
            val sgovOrderId: Long,
        ) {
            val allOrderIdsNewestFirst = listOf(sgovOrderId, spyOrderId, muOrderId)
        }

        fun openAccountWithThreeOrders(username: String): Fixture {
            val (memberId, accountId) = openAccount(username, balance)
            val muOrderId = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 2)
            val spyOrderId = placeOrderSuccessfully(accountId, memberId, "SPY", 60_012, 1)
            val sgovOrderId = placeOrderSuccessfully(accountId, memberId, "SGOV", 10_048, 1)
            cancelOrderSuccessfully(accountId, muOrderId, memberId)
            return Fixture(memberId, accountId, muOrderId, spyOrderId, sgovOrderId)
        }

        beforeEach { resetTables() }

        describe("GET /api/accounts/{accountId}/orders") {
            context("주문이 없으면") {
                it("200과 빈 orders 배열을 응답한다") {
                    val (memberId, accountId) = openAccount("Alice", 0)

                    val response = getOrders(accountId, memberId)

                    response.status shouldBe 200
                    response.body.keys shouldBe setOf("orders")
                    response.orders().shouldBeEmpty()
                }
            }

            context("접수된 주문과 취소된 주문이 섞여 있으면") {
                it("모든 상태의 주문을 최신순(orderId 내림차순)으로 응답한다") {
                    val f = openAccountWithThreeOrders("Alice")

                    val orders = getOrdersSuccessfully(f.accountId, f.memberId)

                    orders.orderIds() shouldBe f.allOrderIdsNewestFirst
                }
                it("ACCEPTED 주문은 orderId, symbol, stockName, side, price, quantity, status, createdAt과 null인 canceledAt을 응답한다") {
                    val f = openAccountWithThreeOrders("Alice")

                    val sgov = getOrdersSuccessfully(f.accountId, f.memberId).first()

                    sgov.keys shouldContainExactlyInAnyOrder
                        listOf("orderId", "symbol", "stockName", "side", "price", "quantity", "status", "createdAt", "canceledAt")
                    sgov["orderId"].asLong() shouldBe f.sgovOrderId
                    sgov["symbol"] shouldBe "SGOV"
                    sgov["stockName"] shouldBe "iShares 0-3 Month Treasury Bond ETF"
                    sgov["side"] shouldBe "BUY"
                    sgov["price"].asLong() shouldBe 10_048
                    sgov["quantity"].asLong() shouldBe 1
                    sgov["status"] shouldBe "ACCEPTED"
                    // 응답 시각은 DB에 저장된 접수 시각과 같다
                    Instant.parse(sgov["createdAt"] as String) shouldBe createdAtOf(f.sgovOrderId)
                    sgov["canceledAt"].shouldBeNull()
                }
                it("CANCELED 주문은 status가 CANCELED이고, canceledAt은 DB에 저장된 취소 시각과 같다") {
                    val f = openAccountWithThreeOrders("Alice")

                    val mu = getOrdersSuccessfully(f.accountId, f.memberId).last()

                    mu["orderId"].asLong() shouldBe f.muOrderId
                    mu["symbol"] shouldBe "MU"
                    mu["stockName"] shouldBe "Micron Technology"
                    mu["price"].asLong() shouldBe 18_743
                    mu["quantity"].asLong() shouldBe 2
                    mu["status"] shouldBe "CANCELED"
                    Instant.parse(mu["createdAt"] as String) shouldBe createdAtOf(f.muOrderId)
                    Instant.parse(mu["canceledAt"].shouldNotBeNull() as String) shouldBe canceledAtOf(f.muOrderId)
                }
            }

            context("다른 회원의 계좌에도 주문이 있으면") {
                it("내 계좌의 주문만 응답한다") {
                    val alice = openAccountWithThreeOrders("Alice")
                    val (bobId, bobAccountId) = openAccount("Bob", balance)
                    // 섞여 나오면 Bob 주문 id가 가장 커서 맨 앞에 온다
                    placeOrderSuccessfully(bobAccountId, bobId, "MU", 18_743, 1)

                    val orders = getOrdersSuccessfully(alice.accountId, alice.memberId)

                    orders.orderIds() shouldBe alice.allOrderIdsNewestFirst
                }
            }

            context("필터를 주면") {
                data class FilterCase(
                    val query: String,
                    val description: String,
                    val expected: (Fixture) -> List<Long>,
                )

                withData(
                    nameFn = { "${it.query} 이면 ${it.description}" },
                    FilterCase("?status=ACCEPTED", "ACCEPTED 주문만 최신순으로 응답한다") { listOf(it.sgovOrderId, it.spyOrderId) },
                    FilterCase("?status=CANCELED", "CANCELED 주문만 응답한다") { listOf(it.muOrderId) },
                    FilterCase("?status=ACCEPTED,CANCELED", "모든 주문을 최신순으로 응답한다") { it.allOrderIdsNewestFirst },
                    FilterCase("?side=BUY", "모든 매수 주문을 최신순으로 응답한다") { it.allOrderIdsNewestFirst },
                    FilterCase("?side=SELL", "매도 주문이 없으므로 빈 배열을 응답한다") { emptyList() },
                    // OR로 합치면 BUY인 3건이 모두 나온다
                    FilterCase("?side=BUY&status=CANCELED", "두 조건을 모두 만족하는 주문만 응답한다") { listOf(it.muOrderId) },
                ) { case ->
                    resetTables()
                    val f = openAccountWithThreeOrders("Alice")

                    val orders = getOrdersSuccessfully(f.accountId, f.memberId, case.query)

                    orders.orderIds() shouldBe case.expected(f)
                }
            }

            context("종목이 다른 주문이 늘어나도") {
                // 주문마다 종목을 따로 읽으면 주문 수만큼 쿼리가 늘어난다(N+1).
                // 종목이 같으면 구현에 따라 한 번만 읽혀 N+1이 가려질 수 있으므로 종목을 모두 다르게 한다
                it("요청 하나에서 실행되는 쿼리 수는 주문이 1건일 때와 같다") {
                    val (memberId, accountId) = openAccount("Alice", 10_000) // $100
                    placeOrderSuccessfully(accountId, memberId, "SPY", 1_000, 1)
                    val oneOrderCount =
                        statementCountOf { getOrdersSuccessfully(accountId, memberId).size shouldBe 1 }

                    listOf("QQQM", "QLD", "TQQQ", "SOXL").forEach { placeOrderSuccessfully(accountId, memberId, it, 1_000, 1) }
                    val fiveOrdersCount =
                        statementCountOf { getOrdersSuccessfully(accountId, memberId).size shouldBe 5 }

                    fiveOrdersCount shouldBe oneOrderCount
                }
            }

            context("없는 계좌면") {
                it("404 ACCOUNT_NOT_FOUND를 응답한다") {
                    val f = openAccountWithThreeOrders("Alice")
                    val missingAccountId = f.accountId + 1

                    val response = getOrders(missingAccountId, f.memberId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", missingAccountId)
                }
            }

            context("남의 계좌면") {
                it("주문이 있어도 없는 계좌와 같은 404 ACCOUNT_NOT_FOUND를 응답한다") {
                    val (aliceId, _) = openAccount("Alice", 0)
                    val bob = openAccountWithThreeOrders("Bob")

                    val response = getOrders(bob.accountId, aliceId)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", bob.accountId)
                }
            }
        }
    })

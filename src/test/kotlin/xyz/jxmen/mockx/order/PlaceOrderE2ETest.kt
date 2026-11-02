package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class PlaceOrderE2ETest(
    @Value("\${local.server.port}") port: Int,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다.
        // accountIdPath는 경로에 그대로 들어간다. 숫자가 아닌 id 같은 형식 오류를 보낼 때 문자열을 넘긴다.
        // 본문은 UTF-8 바이트로 보낸다. mü, ſpy 같은 비ASCII 심볼이 문자셋 변환 없이 그대로 전달되게 하기 위해서다
        fun placeOrderRaw(
            accountIdPath: String,
            json: String,
            memberIdHeader: String?,
        ): ApiResponse =
            client
                .post()
                .uri("/api/accounts/$accountIdPath/orders")
                .headers { headers -> memberIdHeader?.let { headers.set("X-Member-Id", it) } }
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

        fun placeOrderRaw(
            accountId: Long,
            json: String,
            memberIdHeader: String?,
        ): ApiResponse = placeOrderRaw(accountId.toString(), json, memberIdHeader)

        fun orderJson(
            symbol: String,
            price: Long,
            quantity: Long,
            side: String = "BUY",
        ): String =
            mapper.writeValueAsString(
                mapOf("symbol" to symbol, "side" to side, "price" to price, "quantity" to quantity),
            )

        fun placeOrder(
            accountId: Long,
            memberId: Long,
            symbol: String,
            price: Long,
            quantity: Long,
            side: String = "BUY",
        ): ApiResponse = placeOrderRaw(accountId, orderJson(symbol, price, quantity, side), memberId.toString())

        // 접수 성공을 전제로 하는 테스트용. 201이 아니면 바로 실패시켜 뒤의 검증이 엉뚱한 이유로 실패하지 않게 한다
        fun placeOrderSuccessfully(
            accountId: Long,
            memberId: Long,
            symbol: String,
            price: Long,
            quantity: Long,
        ): ApiResponse = placeOrder(accountId, memberId, symbol, price, quantity).also { it.status shouldBe 201 }

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

        fun ledgerCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger", Int::class.java)!!

        fun stockIdOf(symbol: String): Long =
            jdbcTemplate.queryForObject("SELECT id FROM stock WHERE symbol = ?", Long::class.java, symbol)!!

        // hold 불변식의 오른쪽. 그 계좌의 ACCEPTED 주문들의 price × quantity 합계
        fun acceptedOrderAmountOf(accountId: Long): Long =
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(price * quantity), 0) FROM orders WHERE account_id = ? AND status = 'ACCEPTED'",
                Long::class.java,
                accountId,
            )!!

        fun Any?.asLong(): Long = (this as Number).toLong()

        // 주문이 접수되지 않았는지 확인한다. 예수금은 채운 그대로이고, hold와 주문은 없다
        fun shouldHaveNoOrder(
            accountId: Long,
            balance: Long,
        ) {
            balanceOf(accountId) shouldBe balance
            holdOf(accountId) shouldBe 0
            orderCount() shouldBe 0
        }

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
            body["instance"] shouldBe "/api/accounts/$accountIdPath/orders"
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
            context("주문가능금액 안에서 MU 10주를 $187.43에 매수하면") {
                it("201과 orderId, symbol, side, price, quantity, status를 반환한다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000) // $2,000

                    val response = placeOrder(accountId, memberId, "MU", 18_743, 10)

                    response.status shouldBe 201
                    response.body["orderId"].shouldNotBeNull()
                    response.body["symbol"] shouldBe "MU"
                    response.body["side"] shouldBe "BUY"
                    response.body["price"].asLong() shouldBe 18_743
                    response.body["quantity"].asLong() shouldBe 10
                    response.body["status"] shouldBe "ACCEPTED"
                }
                it("응답 본문에는 orderId, symbol, side, price, quantity, status만 있다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    val response = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 10)

                    response.body.keys shouldContainExactlyInAnyOrder
                        listOf("orderId", "symbol", "side", "price", "quantity", "status")
                }
                it("hold 금액이 price × quantity만큼 늘고, 예수금과 원장은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 10)

                    holdOf(accountId) shouldBe 187_430 // 18,743 × 10
                    balanceOf(accountId) shouldBe 200_000
                    ledgerCount() shouldBe 1 // 예수금을 채운 입금 1건. hold는 원장에 남지 않는다
                }
                it("ACCEPTED 매수 주문 1건이 저장되고, 응답의 orderId는 그 주문의 id다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    val response = placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 10)

                    orderCount() shouldBe 1
                    val order =
                        jdbcTemplate.queryForMap(
                            "SELECT id, account_id, stock_id, side, price, quantity, status, canceled_at FROM orders",
                        )
                    order["id"].asLong() shouldBe response.body["orderId"].asLong()
                    order["account_id"].asLong() shouldBe accountId
                    order["stock_id"].asLong() shouldBe stockIdOf("MU") // 티커가 아니라 stock.id를 저장한다
                    order["side"] shouldBe "BUY"
                    order["price"].asLong() shouldBe 18_743
                    order["quantity"].asLong() shouldBe 10
                    order["status"] shouldBe "ACCEPTED"
                    order["canceled_at"].shouldBeNull()
                }
            }

            context("같은 계좌에 주문을 여러 건 넣으면") {
                it("hold 금액은 ACCEPTED 주문들의 price × quantity 합계와 같고, 예수금은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", 1_000_000) // $10,000

                    placeOrderSuccessfully(accountId, memberId, "MU", 18_743, 10) // 187,430
                    placeOrderSuccessfully(accountId, memberId, "SPY", 60_012, 2) // 120,024
                    placeOrderSuccessfully(accountId, memberId, "SGOV", 10_050, 5) // 50,250

                    holdOf(accountId) shouldBe 357_704 // 187,430 + 120,024 + 50,250
                    holdOf(accountId) shouldBe acceptedOrderAmountOf(accountId)
                    balanceOf(accountId) shouldBe 1_000_000
                }
            }

            context("symbol에 소문자가 섞여 있으면") {
                // 콜레이션이 대소문자를 무시해서 종목 조회는 정규화 없이도 성공한다. 응답 symbol이 요청값 그대로인 구현을 잡는다
                withData(
                    nameFn = { "$it 이면 MU로 접수되고 응답 symbol은 MU다" },
                    "mu",
                    "Mu",
                ) { symbol ->
                    resetTables() // withData 항목은 container로 등록돼 beforeEach가 돌지 않는다
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    val response = placeOrder(accountId, memberId, symbol, 18_743, 10)

                    response.status shouldBe 201
                    response.body["symbol"] shouldBe "MU"
                    jdbcTemplate.queryForObject("SELECT stock_id FROM orders", Long::class.java) shouldBe stockIdOf("MU")
                }
            }

            context("side에 소문자가 섞여 있으면") {
                withData(
                    nameFn = { "$it 이면 BUY로 접수되고 응답과 DB의 side는 BUY다" },
                    "buy",
                    "Buy",
                ) { side ->
                    resetTables()
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    val response = placeOrder(accountId, memberId, "MU", 18_743, 10, side)

                    response.status shouldBe 201
                    response.body["side"] shouldBe "BUY"
                    jdbcTemplate.queryForObject("SELECT side FROM orders", String::class.java) shouldBe "BUY"
                    holdOf(accountId) shouldBe 187_430
                }
            }

            context("이미 hold된 금액 때문에 주문가능금액이 모자라면") {
                it("예수금보다 작은 주문이어도 422 INSUFFICIENT_AVAILABLE_AMOUNT를 응답하고, hold와 주문은 그대로다") {
                    val (memberId, accountId) = openAccount("Alice", 10_000) // $100
                    placeOrderSuccessfully(accountId, memberId, "MU", 6_000, 1) // 주문가능금액 10,000 - 6,000 = 4,000

                    // 예수금 10,000보다는 작지만 주문가능금액 4,000보다 크다
                    val response = placeOrder(accountId, memberId, "MU", 5_000, 1)

                    response.shouldBeProblem(422, "INSUFFICIENT_AVAILABLE_AMOUNT", accountId)
                    holdOf(accountId) shouldBe 6_000
                    orderCount() shouldBe 1
                    balanceOf(accountId) shouldBe 10_000
                }
            }

            context("없는 계좌면") {
                it("404 ACCOUNT_NOT_FOUND를 응답하고, 주문은 접수되지 않는다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000)
                    val missingAccountId = accountId + 1

                    val response = placeOrder(missingAccountId, memberId, "MU", 18_743, 10)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", missingAccountId)
                    orderCount() shouldBe 0
                }
            }

            context("남의 계좌면") {
                it("없는 계좌와 같은 404 ACCOUNT_NOT_FOUND를 응답하고, 그 계좌의 hold와 주문은 그대로다") {
                    val (aliceId, _) = openAccount("Alice", 0)
                    // 주문가능금액이 있어서 소유자 확인이 없으면 접수된다
                    val (_, bobAccountId) = openAccount("Bob", 200_000)

                    val response = placeOrder(bobAccountId, aliceId, "MU", 18_743, 10)

                    response.shouldBeProblem(404, "ACCOUNT_NOT_FOUND", bobAccountId)
                    shouldHaveNoOrder(bobAccountId, balance = 200_000)
                }
            }

            context("형식은 맞지만 없는 종목이면") {
                it("404 STOCK_NOT_FOUND를 응답하고, 주문은 접수되지 않는다") {
                    val (memberId, accountId) = openAccount("Alice", 200_000)

                    val response = placeOrder(accountId, memberId, "AAPL", 18_743, 10)

                    response.shouldBeProblem(404, "STOCK_NOT_FOUND", accountId)
                    shouldHaveNoOrder(accountId, balance = 200_000)
                }
            }

            context("없는 종목으로 남의 계좌에 주문하면") {
                // 종목 조회가 계좌 조회·락보다 먼저다. 없는 종목 요청 때문에 계좌 락을 잡지 않는다 (docs/db-design-order.md 6장)
                it("계좌보다 종목을 먼저 확인해 404 STOCK_NOT_FOUND를 응답한다") {
                    val (aliceId, _) = openAccount("Alice", 0)
                    val (_, bobAccountId) = openAccount("Bob", 200_000)

                    val response = placeOrder(bobAccountId, aliceId, "AAPL", 18_743, 10)

                    response.shouldBeProblem(404, "STOCK_NOT_FOUND", bobAccountId)
                    shouldHaveNoOrder(bobAccountId, balance = 200_000)
                }
            }
        }
    })

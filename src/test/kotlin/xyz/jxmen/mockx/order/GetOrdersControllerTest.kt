package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

/**
 * 내 주문 목록 조회 API의 웹 계층(필터 값 검증·정규화, 인증 헤더, 경로 변수 형식, 요청 → Command 변환, 결과 → 응답 변환)만 검증한다.
 * 서비스는 mockk로 대신하므로 DB 없이 돈다. 필터 결과, 정렬, 소유권, 쿼리 수는 GetOrdersE2ETest가 맡는다.
 *
 * - 필터는 쉼표로 여러 값을 받는다(?status=ACCEPTED,CANCELED). 값은 Spring 기본 enum 변환을 따라 대소문자를 구분한다.
 * - 필터를 주지 않으면 null로 넘겨 거르지 않는다. 빈 값(?status=)과 허용하지 않는 값은 400 INVALID_INPUT이다.
 * - side=SELL은 아직 매도 주문이 없어도 조회 조건으로는 허용한다.
 * - "조회되지 않는다"는 서비스가 호출되지 않았는지로 확인한다.
 */
@WebMvcTest(OrderController::class)
@Import(OrderServiceMockConfig::class)
class GetOrdersControllerTest(
    mockMvc: MockMvc,
    getOrdersService: GetOrdersService,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()

        val memberId = 7L
        val accountId = 1L

        data class Response(
            val status: Int,
            val contentType: String?,
            val body: Map<String, Any?>,
        )

        // query는 "?status=ACCEPTED"처럼 물음표부터 그대로 붙인다. 경로 변수는 형식 오류를 보낼 수 있게 문자열로 받는다
        fun getOrdersRaw(
            query: String = "",
            accountIdPath: String = accountId.toString(),
            memberIdHeader: String? = memberId.toString(),
        ): Response {
            val response =
                mockMvc
                    .get("/api/accounts/$accountIdPath/orders$query") {
                        memberIdHeader?.let { header("X-Member-Id", it) }
                    }.andReturn()
                    .response
            val body = response.getContentAsString(Charsets.UTF_8)
            return Response(
                status = response.status,
                contentType = response.contentType,
                body = if (body.isBlank()) emptyMap() else mapper.readValue(body),
            )
        }

        // RFC 9457 Problem Details 공통 형식을 검증한다. instance에는 쿼리 문자열이 붙지 않는다
        fun Response.shouldBeProblem(
            status: Int,
            code: String,
            accountIdPath: Any = accountId,
        ) {
            this.status shouldBe status
            MediaType.parseMediaType(contentType.shouldNotBeNull()).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/accounts/$accountIdPath/orders"
        }

        fun shouldNotGetOrders() {
            verify(exactly = 0) { getOrdersService.getOrders(any()) }
        }

        // 목은 컨텍스트에 하나라 테스트마다 기록을 지운다.
        // withData 항목은 container로 등록돼 beforeEach가 돌지 않으므로 각 항목에서도 직접 부른다
        fun resetMock() {
            clearMocks(getOrdersService)
        }

        beforeEach { resetMock() }

        describe("GET /api/accounts/{accountId}/orders") {
            context("필터 없이 요청하면") {
                it("필터가 null인 Command로 서비스를 호출하고, 주문 목록을 orders 배열에 담아 200으로 응답한다") {
                    val command = slot<GetOrdersCommand>()
                    every { getOrdersService.getOrders(capture(command)) } returns
                        listOf(
                            OrderSummary(
                                id = 2,
                                symbol = "SPY",
                                stockName = "SPDR S&P 500 ETF Trust",
                                side = OrderSide.BUY,
                                price = 60_012,
                                quantity = 1,
                                status = OrderStatus.ACCEPTED,
                                createdAt = Instant.parse("2026-10-31T01:05:00.654321Z"),
                                canceledAt = null,
                            ),
                            OrderSummary(
                                id = 1,
                                symbol = "MU",
                                stockName = "Micron Technology",
                                side = OrderSide.BUY,
                                price = 18_743,
                                quantity = 2,
                                status = OrderStatus.CANCELED,
                                createdAt = Instant.parse("2026-10-31T01:00:00.123456Z"),
                                canceledAt = Instant.parse("2026-10-31T01:02:03.123456Z"),
                            ),
                        )

                    val response = getOrdersRaw()

                    response.status shouldBe 200
                    command.captured shouldBe GetOrdersCommand(accountId = accountId, memberId = memberId, sides = null, statuses = null)
                    // 서비스가 돌려준 순서 그대로 응답한다. 취소되지 않은 주문의 canceledAt은 필드를 빼지 않고 null로 둔다
                    response.body shouldBe
                        mapOf(
                            "orders" to
                                listOf(
                                    mapOf(
                                        "orderId" to 2, // Jackson은 int 범위의 정수를 Int로 읽는다
                                        "symbol" to "SPY",
                                        "stockName" to "SPDR S&P 500 ETF Trust",
                                        "side" to "BUY",
                                        "price" to 60_012,
                                        "quantity" to 1,
                                        "status" to "ACCEPTED",
                                        "createdAt" to "2026-10-31T01:05:00.654321Z",
                                        "canceledAt" to null,
                                    ),
                                    mapOf(
                                        "orderId" to 1,
                                        "symbol" to "MU",
                                        "stockName" to "Micron Technology",
                                        "side" to "BUY",
                                        "price" to 18_743,
                                        "quantity" to 2,
                                        "status" to "CANCELED",
                                        "createdAt" to "2026-10-31T01:00:00.123456Z",
                                        "canceledAt" to "2026-10-31T01:02:03.123456Z",
                                    ),
                                ),
                        )
                }
            }

            context("필터를 주면") {
                data class FilterCase(
                    val query: String,
                    val sides: Set<OrderSide>?,
                    val statuses: Set<OrderStatus>?,
                )

                withData(
                    nameFn = { "${it.query} 이면 sides=${it.sides}, statuses=${it.statuses}인 Command로 서비스를 호출한다" },
                    FilterCase("?side=BUY", setOf(OrderSide.BUY), null),
                    // 매도 주문은 아직 접수할 수 없지만 조회 조건으로는 허용한다. 매도를 추가해도 계약이 바뀌지 않는다
                    FilterCase("?side=SELL", setOf(OrderSide.SELL), null),
                    FilterCase("?status=ACCEPTED,CANCELED", null, setOf(OrderStatus.ACCEPTED, OrderStatus.CANCELED)),
                    FilterCase("?side=BUY&status=CANCELED", setOf(OrderSide.BUY), setOf(OrderStatus.CANCELED)),
                ) { case ->
                    resetMock()
                    val command = slot<GetOrdersCommand>()
                    every { getOrdersService.getOrders(capture(command)) } returns emptyList()

                    val response = getOrdersRaw(query = case.query)

                    response.status shouldBe 200
                    command.captured shouldBe
                        GetOrdersCommand(accountId = accountId, memberId = memberId, sides = case.sides, statuses = case.statuses)
                    response.body shouldBe mapOf("orders" to emptyList<Any>())
                }
            }

            context("필터에 허용하지 않는 값이 있으면") {
                // 무시하고 전체를 돌려주면 오타 난 필터가 "필터 없음"처럼 동작해 버그가 숨는다
                withData(
                    nameFn = { "$it 이면 400 INVALID_INPUT을 응답하고, 조회하지 않는다" },
                    "?status=FILLED",
                    "?side=foo",
                ) { query ->
                    resetMock()

                    val response = getOrdersRaw(query = query)

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    shouldNotGetOrders()
                }
            }

            context("필터 값의 대소문자가 enum 이름과 다르면") {
                // 조회 필터는 Spring 기본 enum 변환(대소문자 구분)을 따른다. 본문을 Jackson으로 읽는 주문 접수와 달리 정규화하지 않는다
                withData(
                    nameFn = { "$it 이면 400 INVALID_INPUT을 응답하고, 조회하지 않는다" },
                    "?side=buy",
                    "?status=Accepted",
                ) { query ->
                    resetMock()

                    val response = getOrdersRaw(query = query)

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    shouldNotGetOrders()
                }
            }

            context("필터 값이 비어 있으면") {
                // "아무것도 고르지 않음"이 "전체"와 같은 뜻이 되지 않게 거절한다
                withData(
                    nameFn = { "'$it' 이면 400 INVALID_INPUT을 응답하고, 조회하지 않는다" },
                    "?status=",
                    "?status=ACCEPTED,",
                    "?side=",
                ) { query ->
                    resetMock()

                    val response = getOrdersRaw(query = query)

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    shouldNotGetOrders()
                }
            }

            context("경로의 계좌 id가 숫자가 아니면") {
                it("400 INVALID_INPUT을 응답하고, 조회하지 않는다") {
                    val response = getOrdersRaw(accountIdPath = "abc")

                    response.shouldBeProblem(400, "INVALID_INPUT", accountIdPath = "abc")
                    shouldNotGetOrders()
                }
            }

            context("X-Member-Id 헤더가 없거나 양의 정수가 아니면") {
                withData(
                    nameFn = { "헤더 ${it ?: "없음"} 이면 401 UNAUTHENTICATED를 응답하고, 조회하지 않는다" },
                    null,
                    "abc",
                ) { memberIdHeader ->
                    resetMock()

                    val response = getOrdersRaw(memberIdHeader = memberIdHeader)

                    response.shouldBeProblem(401, "UNAUTHENTICATED")
                    shouldNotGetOrders()
                }
            }
        }
    })

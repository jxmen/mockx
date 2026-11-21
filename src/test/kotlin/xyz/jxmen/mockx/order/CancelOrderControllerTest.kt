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
import org.springframework.test.web.servlet.post
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

/**
 * 주문 취소 API의 웹 계층(인증 헤더, 경로 변수 형식, 요청 → Command 변환, 결과 → 응답 변환)만 검증한다.
 * 서비스는 mockk로 대신하므로 DB 없이 돈다. hold·주문 상태 같은 정합성은 CancelOrderE2ETest가 맡는다.
 *
 * - 본문이 없는 요청이라 입력 검증은 경로 변수와 헤더뿐이다. (docs/design/order.md 7장, 10장)
 * - "취소되지 않는다"는 서비스가 호출되지 않았는지로 확인한다.
 */
@WebMvcTest(OrderController::class)
@Import(OrderServiceMockConfig::class)
class CancelOrderControllerTest(
    mockMvc: MockMvc,
    cancelOrderService: CancelOrderService,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()

        val memberId = 7L
        val accountId = 1L
        val orderId = 3L

        data class Response(
            val status: Int,
            val contentType: String?,
            val body: Map<String, Any?>,
        )

        // 경로 변수는 문자열로 그대로 넣는다. 숫자가 아닌 id 같은 형식 오류를 보낼 때 문자열을 넘긴다
        fun cancelOrderRaw(
            accountIdPath: String = accountId.toString(),
            orderIdPath: String = orderId.toString(),
            memberIdHeader: String? = memberId.toString(),
        ): Response {
            val response =
                mockMvc
                    .post("/api/accounts/$accountIdPath/orders/$orderIdPath/cancel") {
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

        // RFC 9457 Problem Details 공통 형식을 검증한다
        fun Response.shouldBeProblem(
            status: Int,
            code: String,
            accountIdPath: Any = accountId,
            orderIdPath: Any = orderId,
        ) {
            this.status shouldBe status
            MediaType.parseMediaType(contentType.shouldNotBeNull()).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe "/api/accounts/$accountIdPath/orders/$orderIdPath/cancel"
        }

        fun shouldNotCancelOrder() {
            verify(exactly = 0) { cancelOrderService.cancelOrder(any()) }
        }

        // 목은 컨텍스트에 하나라 테스트마다 기록을 지운다.
        // withData 항목은 container로 등록돼 beforeEach가 돌지 않으므로 각 항목에서도 직접 부른다
        fun resetMock() {
            clearMocks(cancelOrderService)
        }

        beforeEach { resetMock() }

        describe("POST /api/accounts/{accountId}/orders/{orderId}/cancel") {
            context("경로와 헤더가 올바르면") {
                it("경로·헤더 값이 그대로 담긴 Command로 서비스를 호출하고, 취소된 주문을 200으로 응답한다") {
                    val canceledAt = Instant.parse("2026-10-31T01:02:03.123456Z")
                    val command = slot<CancelOrderCommand>()
                    every { cancelOrderService.cancelOrder(capture(command)) } returns
                        CanceledOrder(orderId, "MU", OrderSide.BUY, 18_743, 2, OrderStatus.CANCELED, canceledAt)

                    val response = cancelOrderRaw()

                    response.status shouldBe 200
                    command.captured shouldBe CancelOrderCommand(accountId = accountId, memberId = memberId, orderId = orderId)
                    response.body shouldBe
                        mapOf(
                            "orderId" to orderId.toInt(), // Jackson은 int 범위의 정수를 Int로 읽는다
                            "symbol" to "MU",
                            "side" to "BUY",
                            "price" to 18_743,
                            "quantity" to 2,
                            "status" to "CANCELED",
                            "canceledAt" to "2026-10-31T01:02:03.123456Z",
                        )
                }
            }

            context("경로의 주문 id가 숫자가 아니거나 Long 범위를 넘으면") {
                withData(
                    nameFn = { "주문 id '$it' 이면 400 INVALID_INPUT을 응답하고, 주문은 취소되지 않는다" },
                    "abc",
                    "1.5",
                    "9223372036854775808", // Long.MAX_VALUE + 1. 변환 예외가 500으로 새지 않아야 한다
                ) { orderIdPath ->
                    resetMock()

                    val response = cancelOrderRaw(orderIdPath = orderIdPath)

                    response.shouldBeProblem(400, "INVALID_INPUT", orderIdPath = orderIdPath)
                    shouldNotCancelOrder()
                }
            }

            context("경로의 계좌 id가 숫자가 아니면") {
                it("400 INVALID_INPUT을 응답하고, 주문은 취소되지 않는다") {
                    val response = cancelOrderRaw(accountIdPath = "abc")

                    response.shouldBeProblem(400, "INVALID_INPUT", accountIdPath = "abc")
                    shouldNotCancelOrder()
                }
            }

            context("X-Member-Id 헤더가 없거나 양의 정수가 아니면") {
                withData(
                    nameFn = { "헤더 ${it ?: "없음"} 이면 401 UNAUTHENTICATED를 응답하고, 주문은 취소되지 않는다" },
                    null,
                    "abc",
                ) { memberIdHeader ->
                    resetMock()

                    val response = cancelOrderRaw(memberIdHeader = memberIdHeader)

                    response.shouldBeProblem(401, "UNAUTHENTICATED")
                    shouldNotCancelOrder()
                }
            }
        }
    })

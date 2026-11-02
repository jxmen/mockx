package xyz.jxmen.mockx.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
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

/**
 * 주문 접수 API의 웹 계층(입력 검증, 인증 헤더, 경로 변수, 요청 → Command 변환)만 검증한다.
 * 서비스는 mockk로 대신하므로 DB 없이 돈다. hold·주문 저장 같은 정합성은 PlaceOrderE2ETest가 맡는다.
 *
 * - application.yml의 Jackson 설정(enum 숫자 거절, enum 대소문자 무시, 소수 → 정수 거절)은 @WebMvcTest에도 적용된다.
 * - "주문이 접수되지 않는다"는 서비스가 호출되지 않았는지로 확인한다.
 */
@WebMvcTest(OrderController::class)
@Import(OrderServiceMockConfig::class)
class OrderControllerTest(
    mockMvc: MockMvc,
    placeOrderService: PlaceOrderService,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()

        val maxPrice = 1_000_000L // $10,000
        val maxQuantity = 10_000L
        val memberId = 7L
        val accountId = 1L

        data class Response(
            val status: Int,
            val contentType: String?,
            val body: Map<String, Any?>,
        )

        // accountIdPath는 경로에 그대로 들어간다. 숫자가 아닌 id 같은 형식 오류를 보낼 때 문자열을 넘긴다.
        // 본문은 UTF-8 바이트로 보낸다. mü, ſpy 같은 비ASCII 심볼이 문자셋 변환 없이 그대로 전달되게 하기 위해서다
        fun placeOrderRaw(
            json: String,
            accountIdPath: String = accountId.toString(),
            memberIdHeader: String? = memberId.toString(),
        ): Response {
            val response =
                mockMvc
                    .post("/api/accounts/$accountIdPath/orders") {
                        contentType = MediaType.APPLICATION_JSON
                        content = json.toByteArray(Charsets.UTF_8)
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

        fun orderJson(
            symbol: String = "MU",
            price: Long = 18_743,
            quantity: Long = 10,
            side: String = "BUY",
        ): String =
            mapper.writeValueAsString(
                mapOf("symbol" to symbol, "side" to side, "price" to price, "quantity" to quantity),
            )

        // RFC 9457 Problem Details 공통 형식을 검증한다
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

        @Suppress("UNCHECKED_CAST")
        fun Response.errorFields(): List<String> =
            (body["errors"].shouldNotBeNull() as List<Map<String, Any?>>).map { it["field"] as String }

        fun shouldNotPlaceOrder() {
            verify(exactly = 0) { placeOrderService.placeOrder(any()) }
        }

        // 목은 컨텍스트에 하나라 테스트마다 기록을 지운다.
        // withData 항목은 container로 등록돼 beforeEach가 돌지 않으므로 각 항목에서도 직접 부른다
        fun resetMock() {
            clearMocks(placeOrderService)
        }

        beforeEach { resetMock() }

        describe("POST /api/accounts/{accountId}/orders") {
            context("price·quantity가 허용 범위의 경계값이면") {
                withData(
                    nameFn = { (price, quantity) ->
                        "price $price, quantity $quantity 이면 경로·헤더·본문 값이 그대로 담긴 Command로 서비스를 호출하고 201을 응답한다"
                    },
                    1L to 1L, // 둘 다 최소
                    maxPrice to 1L, // price 상한 $10,000
                    1L to maxQuantity, // quantity 상한 10,000주
                ) { (price, quantity) ->
                    resetMock()
                    val command = slot<PlaceOrderCommand>()
                    every { placeOrderService.placeOrder(capture(command)) } returns
                        PlacedOrder(1L, "MU", OrderSide.BUY, price, quantity, OrderStatus.ACCEPTED)

                    val response = placeOrderRaw(orderJson(price = price, quantity = quantity))

                    response.status shouldBe 201
                    command.captured shouldBe
                        PlaceOrderCommand(
                            accountId = accountId,
                            memberId = memberId,
                            symbol = "MU",
                            side = OrderSide.BUY,
                            price = price,
                            quantity = quantity,
                        )
                }
            }

            context("price가 허용 범위를 벗어나면") {
                withData(
                    nameFn = { (price, quantity) ->
                        "price $price, quantity $quantity 이면 price가 errors에 담긴 400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다"
                    },
                    0L to 1L,
                    -1L to 1L,
                    Long.MIN_VALUE to 1L, // 부호만 보고 거절해야 한다
                    maxPrice + 1 to 1L, // 상한 + 1센트
                    Long.MAX_VALUE to 1L,
                    // 2^62 + 1. × 4가 오버플로해 4가 된다. 상한 검사가 없으면 4센트만 hold하고 접수된다 (docs/db-design-order.md 6장)
                    4_611_686_018_427_387_905L to 4L,
                ) { (price, quantity) ->
                    resetMock()

                    val response = placeOrderRaw(orderJson(price = price, quantity = quantity))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "price"
                    shouldNotPlaceOrder()
                }
            }

            context("quantity가 허용 범위를 벗어나면") {
                withData(
                    nameFn = { "quantity $it 이면 quantity가 errors에 담긴 400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다" },
                    0L,
                    -1L,
                    maxQuantity + 1, // 상한 + 1주
                ) { quantity ->
                    resetMock()

                    val response = placeOrderRaw(orderJson(price = 1, quantity = quantity))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "quantity"
                    shouldNotPlaceOrder()
                }
            }

            // 숫자 형식 오류는 역직렬화 단계에서 실패한다. 어떤 예외로 오는지에 따라 errors가 없을 수 있어서 상태와 code만 본다
            context("price·quantity가 정수가 아니거나 Long 범위를 넘으면") {
                withData(
                    nameFn = { (label, _) -> "$label 이면 400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다" },
                    // 달러로 착각해 보낸 소수. 187센트로 잘려 접수되면 안 된다
                    "price 187.43" to """{"symbol": "MU", "side": "BUY", "price": 187.43, "quantity": 10}""",
                    // 소수점 주식은 받지 않는다
                    "quantity 1.5" to """{"symbol": "MU", "side": "BUY", "price": 18743, "quantity": 1.5}""",
                    // Long.MAX_VALUE + 1. 파싱 예외가 500으로 새지 않아야 한다
                    "price 9223372036854775808" to """{"symbol": "MU", "side": "BUY", "price": 9223372036854775808, "quantity": 10}""",
                    "price \"abc\"" to """{"symbol": "MU", "side": "BUY", "price": "abc", "quantity": 10}""",
                ) { (_, json) ->
                    resetMock()

                    val response = placeOrderRaw(json)

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    shouldNotPlaceOrder()
                }
            }

            context("필드가 빠졌거나 null이면") {
                withData(
                    nameFn = { (field, omitted) ->
                        "$field ${if (omitted) "누락" else "null"}이면 errors에 $field 필드가 담긴 400 INVALID_INPUT을 응답한다"
                    },
                    "symbol" to true,
                    "symbol" to false,
                    "side" to true,
                    "side" to false,
                    "price" to true,
                    "price" to false,
                    "quantity" to true,
                    "quantity" to false,
                ) { (field, omitted) ->
                    resetMock()
                    val body = mutableMapOf<String, Any?>("symbol" to "MU", "side" to "BUY", "price" to 18_743, "quantity" to 10)
                    if (omitted) {
                        body.remove(field)
                    } else {
                        body[field] = null
                    }

                    val response = placeOrderRaw(mapper.writeValueAsString(body))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain field
                    shouldNotPlaceOrder()
                }
            }

            context("symbol이 영문 1~10자가 아니면") {
                withData(
                    nameFn = { "symbol '$it' 이면 symbol이 errors에 담긴 400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다" },
                    "", // 빈 문자열
                    "ABCDEFGHIJK", // 11자. 컬럼이 VARCHAR(10)이라 있을 수 없는 심볼
                    "MU1", // 숫자
                    "M-U", // 특수문자
                    // 콜레이션이 악센트도 무시해서(ai) 형식 검사가 없으면 MU로 접수된다
                    "mü",
                    // long s(U+017F). 대문자로 바꾸면 S가 되므로 "SPY"로 바꾼 뒤 검사하면 통과해 버린다. 형식 검사는 원래 입력에 한다
                    "ſpy",
                ) { symbol ->
                    resetMock()

                    val response = placeOrderRaw(orderJson(symbol = symbol))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "symbol"
                    shouldNotPlaceOrder()
                }
            }

            context("side가 BUY가 아니면") {
                withData(
                    nameFn = { (label, _) -> "side $label 이면 side가 errors에 담긴 400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다" },
                    // OrderSide enum에는 있지만 매도 흐름이 없다. 생략하거나 잘못 보낸 값과 똑같이 거절한다 (docs/db-design-order.md 12장 9번)
                    "\"SELL\"" to "\"SELL\"",
                    // 대소문자를 무시하므로 소문자 매도도 같은 이유로 거절된다
                    "\"sell\"" to "\"sell\"",
                    "\"HOLD\"" to "\"HOLD\"",
                    "\"\"" to "\"\"",
                    // Jackson은 기본 설정에서 enum을 숫자 ordinal로도 읽는다. 0이 BUY로 접수되면 안 된다
                    "0" to "0",
                ) { (_, sideJson) ->
                    resetMock()
                    val json = """{"symbol": "MU", "side": $sideJson, "price": 18743, "quantity": 10}"""

                    val response = placeOrderRaw(json)

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContain "side"
                    shouldNotPlaceOrder()
                }
            }

            context("price와 quantity가 둘 다 허용 범위를 벗어나면") {
                it("price와 quantity가 모두 errors에 담긴 400 INVALID_INPUT을 응답한다") {
                    val response = placeOrderRaw(orderJson(price = 0, quantity = 0))

                    response.shouldBeProblem(400, "INVALID_INPUT")
                    response.errorFields() shouldContainAll listOf("price", "quantity")
                    shouldNotPlaceOrder()
                }
            }

            context("X-Member-Id 헤더가 없으면") {
                it("401 UNAUTHENTICATED를 응답하고, 주문은 접수되지 않는다") {
                    val response = placeOrderRaw(orderJson(), memberIdHeader = null)

                    response.shouldBeProblem(401, "UNAUTHENTICATED")
                    shouldNotPlaceOrder()
                }
            }

            context("경로의 계좌 id가 숫자가 아니면") {
                it("400 INVALID_INPUT을 응답하고, 주문은 접수되지 않는다") {
                    val response = placeOrderRaw(orderJson(), accountIdPath = "abc")

                    response.shouldBeProblem(400, "INVALID_INPUT", accountIdPath = "abc")
                    shouldNotPlaceOrder()
                }
            }
        }
    })

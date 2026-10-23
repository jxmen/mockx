package xyz.jxmen.mockx.stock

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.member.ApiResponse

/**
 * 종목 목록·상세 조회 API 계약 테스트. 실제 서버(RANDOM_PORT)에 HTTP로 요청하고, Testcontainers MySQL을 쓴다.
 * - 종목 데이터는 Flyway(V4)가 넣은 8개다. 테스트는 데이터를 바꾸지 않는다.
 * - 응답은 { "stocks": [ { "symbol", "name" } ] } 형태이고, 내부 id는 노출하지 않는다.
 * - 종목 마스터는 공개 정보라 인증(X-Member-Id) 없이 조회한다.
 * - 정렬은 기본 id 오름차순이고, sort=필드[,asc|desc]로 바꾼다. 필드는 id, symbol만, 정렬 기준은 하나만 허용한다. 그 밖은 400 INVALID_INPUT.
 * - 상세 조회는 { "symbol", "name" }을 감싸지 않고 응답한다. 요청 심볼은 영문 1~10자만 허용하고(그 밖은 400 INVALID_INPUT),
 *   대문자로 바꿔 조회한다(mu → MU). 없는 종목은 404 STOCK_NOT_FOUND.
 * - DB 콜레이션(utf8mb4_0900_ai_ci)이 대소문자를 무시하므로, 대문자 정규화를 빠뜨려도 소문자 요청 케이스는 통과한다. 이 테스트로는 정규화 누락을 잡지 못한다.
 * - 설정이 DepositE2ETest와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다.
 * - 계약은 docs/db-design-stock.md 1장(식별자), 4.3(대소문자), 4.4(종목 데이터), 4.5(목록 API)를 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class StockE2ETest(
    @Value("\${local.server.port}") port: Int,
) : DescribeSpec({
        val mapper = jacksonObjectMapper()
        val client = RestClient.builder().baseUrl("http://localhost:$port").build()

        // 4xx, 5xx에도 예외를 던지지 않고 상태 코드와 본문을 그대로 돌려받는다
        // sorts는 sort 쿼리 파라미터 값들이다. 여러 개를 넘기면 sort=a&sort=b로 보낸다
        fun RestClient.RequestHeadersSpec<*>.toApiResponse(): ApiResponse =
            exchange { _, response ->
                val body = response.bodyTo(String::class.java)
                ApiResponse(
                    status = response.statusCode.value(),
                    headers = response.headers,
                    body = if (body.isNullOrBlank()) emptyMap() else mapper.readValue(body),
                )
            }

        fun getStocks(vararg sorts: String): ApiResponse =
            client
                .get()
                .uri { builder -> builder.path("/api/stocks").apply { sorts.forEach { queryParam("sort", it) } }.build() }
                .toApiResponse()

        // symbol은 경로 변수로 넘겨서 퍼센트 인코딩된다 (mü → m%C3%BC, 공백 → %20)
        fun getStock(symbol: String): ApiResponse = client.get().uri("/api/stocks/{symbol}", symbol).toApiResponse()

        @Suppress("UNCHECKED_CAST")
        fun ApiResponse.stocks(): List<Map<String, Any?>> = body["stocks"].shouldNotBeNull() as List<Map<String, Any?>>

        fun ApiResponse.symbols(): List<Any?> = stocks().map { it["symbol"] }

        // RFC 9457 Problem Details 공통 형식을 검증한다. instance에는 쿼리 문자열이 붙지 않는다
        fun ApiResponse.shouldBeProblem(
            status: Int,
            code: String,
            instance: String = "/api/stocks",
        ) {
            this.status shouldBe status
            headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            body["status"] shouldBe status
            body["code"] shouldBe code
            body["instance"] shouldBe instance
        }

        // V4에서 넣은 순서. id 오름차순과 같다
        val insertedOrder = listOf("SPY", "QQQM", "QLD", "TQQQ", "SOXL", "SGOV", "MU", "SKHY")
        val symbolAscending = listOf("MU", "QLD", "QQQM", "SGOV", "SKHY", "SOXL", "SPY", "TQQQ")

        describe("GET /api/stocks") {
            context("요청하면") {
                it("200과 Flyway로 넣은 종목 8개를 반환한다") {
                    val response = getStocks()

                    response.status shouldBe 200
                    response.headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_JSON) shouldBe true
                    response.stocks() shouldHaveSize 8
                }
                it("응답 본문에는 stocks만 있고, 각 종목에는 symbol, name만 있다") {
                    val response = getStocks()

                    response.body.keys shouldContainExactlyInAnyOrder listOf("stocks")
                    // 내부 id, 생성·수정 시각은 노출하지 않는다 (docs/db-design-stock.md 1장)
                    response.stocks().forEach { stock ->
                        stock.keys shouldContainExactlyInAnyOrder listOf("symbol", "name")
                    }
                }
                it("종목의 symbol과 name이 DB 값과 같다") {
                    val response = getStocks()

                    val mu = response.stocks().single { it["symbol"] == "MU" }
                    mu["name"] shouldBe "Micron Technology"
                }
            }

            context("X-Member-Id 헤더 없이 요청하면") {
                it("공개 API라서 401이 아니라 200을 반환한다") {
                    // getStocks는 헤더를 보내지 않는다. @CurrentMember를 붙이면 401이 나서 실패한다
                    getStocks().status shouldBe 200
                }
            }

            context("sort가 없으면") {
                it("id 오름차순(넣은 순서)으로 정렬한다") {
                    getStocks().symbols() shouldBe insertedOrder
                }
            }

            context("sort가 허용된 필드와 방향이면") {
                withData(
                    nameFn = { (sort, _) -> "sort=$sort 이면 그 순서로 정렬한다" },
                    "symbol" to symbolAscending, // 방향을 생략하면 오름차순
                    "symbol,asc" to symbolAscending,
                    "symbol,desc" to symbolAscending.reversed(),
                    "symbol,DESC" to symbolAscending.reversed(), // 방향은 대소문자를 구분하지 않는다
                    "id" to insertedOrder,
                    "id,asc" to insertedOrder,
                    "id,ASC" to insertedOrder,
                    "id,desc" to insertedOrder.reversed(),
                ) { (sort, expected) ->
                    val response = getStocks(sort)

                    response.status shouldBe 200
                    response.symbols() shouldBe expected
                }
            }

            context("sort가 올바르지 않으면") {
                withData(
                    nameFn = { "sort=${it.joinToString("&sort=")} 이면 400 INVALID_INPUT을 응답한다" },
                    listOf("createdAt"), // 엔티티에는 있지만 허용하지 않은 필드. 응답에 없는 필드로 정렬하지 않는다
                    listOf("name"), // 응답에는 있지만 허용하지 않은 필드
                    listOf("price"), // 없는 필드. Spring Data에 그대로 넘기면 PropertyReferenceException으로 500이 난다
                    listOf("symbol,up"), // 잘못된 방향
                    listOf("symbol", "id,desc"), // 정렬 기준은 하나만 허용한다
                ) { sorts ->
                    getStocks(*sorts.toTypedArray()).shouldBeProblem(400, "INVALID_INPUT")
                }
            }
        }

        describe("GET /api/stocks/{symbol}") {
            context("있는 종목의 심볼로 요청하면") {
                it("200과 종목의 symbol, name을 반환한다") {
                    val response = getStock("MU")

                    response.status shouldBe 200
                    response.headers.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_JSON) shouldBe true
                    response.body["symbol"] shouldBe "MU"
                    response.body["name"] shouldBe "Micron Technology"
                }
                it("응답 본문에는 symbol, name만 있다") {
                    // 감싸지 않고, 내부 id와 생성·수정 시각은 노출하지 않는다 (docs/db-design-stock.md 1장)
                    getStock("MU").body.keys shouldContainExactlyInAnyOrder listOf("symbol", "name")
                }
            }

            context("X-Member-Id 헤더 없이 요청하면") {
                it("공개 API라서 401이 아니라 200을 반환한다") {
                    getStock("MU").status shouldBe 200
                }
            }

            context("심볼에 소문자가 섞여 있으면") {
                // 콜레이션이 대소문자를 무시해서 정규화 없이도 통과한다. 정규화 누락은 이 테스트로 잡지 못한다 (docs/db-design-stock.md 4.3)
                withData(
                    nameFn = { (symbol, expected) -> "$symbol 이면 대문자로 바꿔 찾고 symbol을 $expected 로 응답한다" },
                    "mu" to "MU",
                    "Mu" to "MU",
                    "sKhY" to "SKHY",
                ) { (symbol, expected) ->
                    val response = getStock(symbol)

                    response.status shouldBe 200
                    response.body["symbol"] shouldBe expected
                }
            }

            context("형식은 맞지만 없는 종목이면") {
                it("404 STOCK_NOT_FOUND를 응답한다") {
                    getStock("AAPL").shouldBeProblem(404, "STOCK_NOT_FOUND", instance = "/api/stocks/AAPL")
                }
                it("10자 심볼은 형식 검사를 통과해 400이 아니라 404를 응답한다") {
                    // 최대 길이 경계값. 11자는 아래에서 400
                    getStock("ABCDEFGHIJ").shouldBeProblem(404, "STOCK_NOT_FOUND", instance = "/api/stocks/ABCDEFGHIJ")
                }
            }

            context("심볼이 영문 1~10자가 아니면") {
                withData(
                    nameFn = { (symbol, _) -> "'$symbol' 이면 400 INVALID_INPUT을 응답한다" },
                    // 콜레이션이 악센트도 무시해서(ai) 형식 검사가 없으면 MU로 200이 나간다. 대문자로 바꿔도 MÜ라 그대로 찾힌다
                    "mü" to "/api/stocks/m%C3%BC",
                    "ABCDEFGHIJK" to "/api/stocks/ABCDEFGHIJK", // 11자. 컬럼이 VARCHAR(10)이라 있을 수 없는 심볼
                    "MU1" to "/api/stocks/MU1", // 숫자
                    "M-U" to "/api/stocks/M-U", // 특수문자
                    "MU " to "/api/stocks/MU%20", // 끝 공백. NO PAD 콜레이션이라 형식 검사가 없으면 404가 된다
                    // long s(U+017F). 대문자로 바꾸면 S가 되므로 "SPY"로 바꾼 뒤 검사하면 통과해 버린다. 형식 검사는 원래 입력에 한다
                    "ſpy" to "/api/stocks/%C5%BFpy",
                ) { (symbol, instance) ->
                    getStock(symbol).shouldBeProblem(400, "INVALID_INPUT", instance = instance)
                }
            }
        }
    })

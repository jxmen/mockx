package xyz.jxmen.mockx.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBePositive
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.common.jpa.JpaConfig

/**
 * 실제 MySQL(Testcontainers)에 Flyway 스키마와 종목 데이터(V4)를 올려 매핑, 데이터, 제약을 검증한다.
 * - 기준 문서: docs/design/stock.md 3장(테이블), 4.2(티커 재사용은 다루지 않음), 4.4(종목 데이터)
 * - 종목 데이터는 Flyway가 넣고, 테스트(it)에서 저장한 행은 롤백된다. 설정은 MemberRepositoryTest와 같다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class, JpaConfig::class)
class StockRepositoryTest(
    stockRepository: StockRepository,
) : DescribeSpec({
        describe("Flyway로 넣은 종목 데이터는") {
            it("문서 4.4의 8개 종목이다") {
                stockRepository.findAll().map { it.symbol } shouldContainExactlyInAnyOrder
                    listOf("SPY", "QQQM", "QLD", "TQQQ", "SOXL", "SGOV", "MU", "SKHY")
            }
        }

        describe("findBySymbol 메서드는") {
            context("있는 심볼이면") {
                it("그 종목을 돌려준다") {
                    val stock = stockRepository.findBySymbol("MU").shouldNotBeNull()

                    stock.id.shouldBePositive()
                    stock.symbol shouldBe "MU"
                    stock.name shouldBe "Micron Technology"
                }
            }
            context("목록에 없는 심볼이면") {
                it("null을 돌려준다") {
                    stockRepository.findBySymbol("AAPL").shouldBeNull()
                }
            }
        }

        describe("save 메서드는") {
            context("이미 있는 심볼로 저장하면") {
                it("uk_stock_symbol 위반이 난다") {
                    // IDENTITY 전략이라 save 시점에 INSERT가 바로 실행되고, 리포지토리 프록시가 예외를 변환한다
                    val exception =
                        shouldThrow<DataIntegrityViolationException> {
                            stockRepository.save(Stock(name = "Duplicated", symbol = "MU"))
                        }

                    exception.message shouldContain "uk_stock_symbol"
                }
            }
        }
    })

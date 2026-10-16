package xyz.jxmen.mockx.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import xyz.jxmen.mockx.TestcontainersConfiguration

/**
 * 계좌번호 순번 발급기 테스트. 실제 MySQL(Testcontainers)에서 LAST_INSERT_ID 동작을 확인한다.
 * - 발급기는 바깥 트랜잭션 안에서만 호출되어야 한다(Propagation.MANDATORY).
 *   UPDATE와 SELECT LAST_INSERT_ID()가 같은 커넥션에서 실행되고, 순번 증가가 바깥 작업과 함께 롤백되게 하기 위해서다.
 * - @JdbcTest는 테스트마다 트랜잭션을 열기 때문에 NOT_SUPPORTED로 끈다.
 *   트랜잭션이 필요한 케이스는 TransactionTemplate으로 직접 연다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class, JdbcAccountNumberRepository::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcAccountNumberRepositoryTest(
    repository: JdbcAccountNumberRepository,
    jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : DescribeSpec({
        val transactionTemplate = TransactionTemplate(transactionManager)

        fun currentSequence(): Long =
            jdbcTemplate.queryForObject(
                "SELECT next_value FROM account_number_sequence WHERE product_code = '100'",
                Long::class.java,
            )!!

        beforeEach {
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        describe("generateAndIncrease") {
            context("트랜잭션 없이 호출하면") {
                it("IllegalTransactionStateException을 던진다") {
                    shouldThrow<IllegalTransactionStateException> {
                        repository.generateAndIncrease()
                    }
                }
                it("순번이 증가하지 않는다") {
                    runCatching { repository.generateAndIncrease() }

                    currentSequence() shouldBe 0
                }
            }

            context("트랜잭션 안에서 호출하면") {
                it("다음 순번으로 계좌번호를 발급한다") {
                    val number = transactionTemplate.execute { repository.generateAndIncrease() }!!

                    number.number shouldBe "100000000016"
                    currentSequence() shouldBe 1
                }
                it("한 트랜잭션에서 연속으로 호출하면 순번이 1씩 증가한다") {
                    val numbers =
                        transactionTemplate.execute {
                            listOf(repository.generateAndIncrease(), repository.generateAndIncrease())
                        }!!

                    numbers.map { it.number } shouldBe listOf("100000000016", "100000000024")
                }
                it("트랜잭션이 롤백되면 순번도 원래대로 돌아간다") {
                    transactionTemplate.execute { status ->
                        repository.generateAndIncrease()
                        status.setRollbackOnly()
                    }

                    currentSequence() shouldBe 0
                }
            }
        }
    })

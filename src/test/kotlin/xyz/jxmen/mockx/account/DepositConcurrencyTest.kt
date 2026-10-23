package xyz.jxmen.mockx.account

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.member.SignupRequest
import xyz.jxmen.mockx.member.SignupService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 입금 동시성 테스트. DepositService를 여러 스레드에서 직접 호출하고, Testcontainers MySQL을 쓴다.
 * - 같은 계좌에 입금이 동시에 몰려도 갱신 손실(lost update) 없이 모든 입금이 예수금과 원장에 반영되어야 한다.
 * - HTTP 계층을 빼고 서비스만 호출해서 트랜잭션과 락 동작만 본다.
 * - 설정이 DepositE2ETest와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다. 포트는 쓰지 않지만 컨텍스트 재사용을 위해 RANDOM_PORT를 맞춘다.
 * - 락 없이 구현하면 실행할 때마다 결과가 달라질 수 있다. 한 번 통과했다고 안전한 것은 아니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class DepositConcurrencyTest(
    depositService: DepositService,
    signupService: SignupService,
    accountRepository: AccountRepository,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        // 요청마다 스레드를 하나씩 둬서 모두 출발선에 세운다. 실제로 동시에 DB에 닿는 수는 커넥션 풀 크기(기본 10)까지다
        val requestCount = 100

        // 가입한 회원의 (memberId, accountId)를 돌려준다
        fun signup(username: String): Pair<Long, Long> {
            val memberId =
                signupService
                    .signup(
                        SignupRequest(
                            nickname = username,
                            username = username,
                            password = "pass1234",
                            passwordConfirm = "pass1234",
                        ),
                    ).memberId
            return memberId to accountRepository.findByMemberId(memberId)!!.id
        }

        // 모든 스레드를 출발선에 세운 뒤 한꺼번에 출발시킨다. 실패한 호출의 예외를 모아 돌려준다
        fun depositConcurrently(
            accountId: Long,
            memberId: Long,
            amount: Long,
        ): List<Throwable> {
            val ready = CountDownLatch(requestCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(requestCount)
            val failures = ConcurrentLinkedQueue<Throwable>()

            Executors.newFixedThreadPool(requestCount).use { executor ->
                repeat(requestCount) {
                    executor.submit {
                        try {
                            ready.countDown()
                            start.await()
                            depositService.deposit(accountId, memberId, amount)
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
            return failures.toList()
        }

        fun balanceOf(accountId: Long): Long =
            jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = ?", Long::class.java, accountId)!!

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.execute("TRUNCATE TABLE ledger")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("DepositService.deposit") {
            context("같은 계좌에 1센트 입금 100건을 동시에 요청하면") {
                it("모든 입금이 성공하고, 예수금은 100센트($1), 원장은 100건이다") {
                    val (memberId, accountId) = signup("Alice")

                    val failures = depositConcurrently(accountId, memberId, 1)

                    failures.shouldBeEmpty()
                    balanceOf(accountId) shouldBe 100 // 0 + 1 × 100
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger WHERE account_id = ?",
                        Int::class.java,
                        accountId,
                    ) shouldBe requestCount
                }
                it("원장 금액 합계는 예수금과 같고, 입금 후 잔액은 1부터 100까지 중복 없이 하나씩 기록된다") {
                    val (memberId, accountId) = signup("Alice")

                    depositConcurrently(accountId, memberId, 1).shouldBeEmpty()

                    val ledgerSum =
                        jdbcTemplate.queryForObject(
                            "SELECT SUM(amount) FROM ledger WHERE account_id = ?",
                            Long::class.java,
                            accountId,
                        )
                    // 입금이 하나씩 차례로 반영됐다면 balance_after는 1, 2, ..., 100이어야 한다. 중복이 있으면 같은 잔액을 보고 덮어쓴 것이다
                    val balanceAfters =
                        jdbcTemplate.queryForList(
                            "SELECT balance_after FROM ledger WHERE account_id = ? ORDER BY balance_after",
                            Long::class.java,
                            accountId,
                        )
                    ledgerSum shouldBe balanceOf(accountId)
                    balanceAfters shouldBe (1L..requestCount).toList()
                }
            }
        }
    })

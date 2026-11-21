package xyz.jxmen.mockx.member

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import xyz.jxmen.mockx.TestcontainersConfiguration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 회원가입 동시성 테스트. SignupService를 여러 스레드에서 직접 호출하고, Testcontainers MySQL을 쓴다.
 * - 같은 username으로 가입이 동시에 몰려도 1건만 성공해야 한다. 조회 후 insert로는 경쟁 조건을 막을 수 없고,
 *   username UNIQUE 제약(`uk_member_username`)이 최종 방어선이다 (docs/design/member.md 3장).
 * - 실패한 요청은 모두 DuplicateUsernameException(→ 409)이어야 한다. 데드락이나 변환되지 않은
 *   DataIntegrityViolationException으로 끝나면 사용자에게 500이 나간다.
 * - 409 응답 형식은 SignupE2ETest가 검증하므로 여기서는 HTTP 계층을 빼고 트랜잭션과 제약 동작만 본다.
 * - 설정이 다른 동시성 테스트와 같아서 Spring 컨텍스트와 MySQL 컨테이너를 재사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class SignupConcurrencyTest(
    signupService: SignupService,
    jdbcTemplate: JdbcTemplate,
) : DescribeSpec({
        // 요청마다 스레드를 하나씩 둬서 모두 출발선에 세운다. 실제로 동시에 DB에 닿는 수는 커넥션 풀 크기(기본 10)까지다
        val requestCount = 100

        fun request(username: String) =
            SignupRequest(
                nickname = "앨리스",
                username = username,
                password = "pass1234",
                passwordConfirm = "pass1234",
            )

        // 모든 스레드를 출발선에 세운 뒤 한꺼번에 출발시킨다. 성공 건수와 실패한 호출의 예외를 돌려준다
        fun signupConcurrently(usernames: List<String>): Pair<Int, List<Throwable>> {
            val ready = CountDownLatch(usernames.size)
            val start = CountDownLatch(1)
            val done = CountDownLatch(usernames.size)
            val successCount = AtomicInteger()
            val failures = ConcurrentLinkedQueue<Throwable>()

            Executors.newFixedThreadPool(usernames.size).use { executor ->
                usernames.forEach { username ->
                    executor.submit {
                        try {
                            ready.countDown()
                            start.await()
                            signupService.signup(request(username))
                            successCount.incrementAndGet()
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
            return successCount.get() to failures.toList()
        }

        fun countRows(table: String): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

        fun currentSequence(): Long =
            jdbcTemplate.queryForObject(
                "SELECT next_value FROM account_number_sequence WHERE product_code = '100'",
                Long::class.java,
            )!!

        fun resetTables() {
            jdbcTemplate.execute("TRUNCATE TABLE member")
            jdbcTemplate.execute("TRUNCATE TABLE account")
            jdbcTemplate.update("UPDATE account_number_sequence SET next_value = 0 WHERE product_code = '100'")
        }

        beforeEach { resetTables() }

        describe("SignupService.signup") {
            context("같은 username으로 100건을 동시에 가입하면") {
                it("1건만 성공하고, 나머지 99건은 모두 DuplicateUsernameException으로 실패한다") {
                    val (successCount, failures) = signupConcurrently(List(requestCount) { "Alice" })

                    successCount shouldBe 1
                    failures shouldHaveSize requestCount - 1 // 100 - 1
                    failures.forAll { it.shouldBeInstanceOf<DuplicateUsernameException>() }
                }
                it("회원과 계좌는 1건씩만 저장된다") {
                    signupConcurrently(List(requestCount) { "Alice" })

                    countRows("member") shouldBe 1
                    countRows("account") shouldBe 1
                }
                it("계좌번호 순번은 성공한 1건만큼만 증가한다") {
                    signupConcurrently(List(requestCount) { "Alice" })

                    // 실패한 99건은 회원 저장에서 막히고 롤백되므로 순번을 쓰지 않는다. 0 + 1
                    currentSequence() shouldBe 1
                }
            }

            context("대소문자만 다른 username 100개로 동시에 가입하면") {
                // "abcdefg"의 각 글자를 i의 비트에 따라 대문자로 바꾼다. 7글자라 128가지 중 서로 다른 100개가 나온다
                val variants =
                    List(requestCount) { i ->
                        "abcdefg"
                            .mapIndexed { bit, c -> if ((i shr bit) and 1 == 1) c.uppercaseChar() else c }
                            .joinToString("")
                    }

                it("만든 username은 문자열로는 모두 다르다") {
                    variants.toSet() shouldHaveSize requestCount
                }
                it("collation이 같은 값으로 보므로 1건만 성공하고, 나머지는 DuplicateUsernameException으로 실패한다") {
                    val (successCount, failures) = signupConcurrently(variants)

                    successCount shouldBe 1
                    failures shouldHaveSize requestCount - 1 // 100 - 1
                    failures.forAll { it.shouldBeInstanceOf<DuplicateUsernameException>() }
                    countRows("member") shouldBe 1
                    countRows("account") shouldBe 1
                }
            }
        }
    })

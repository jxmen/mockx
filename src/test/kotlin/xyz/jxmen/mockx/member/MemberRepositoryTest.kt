package xyz.jxmen.mockx.member

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBePositive
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import xyz.jxmen.mockx.TestcontainersConfiguration
import xyz.jxmen.mockx.common.jpa.JpaConfig

/**
 * 실제 MySQL(Testcontainers)에 Flyway 스키마를 올려 매핑과 제약을 검증한다.
 * - replace = NONE: 내장 DB로 바꾸지 않는다. collation, UNIQUE 제약은 MySQL에서만 검증된다.
 * - JpaConfig: @DataJpaTest는 사용자 @Configuration을 스캔하지 않으므로 auditing을 켜려면 직접 import한다.
 * - 생성자 주입과 테스트(it)별 롤백은 ProjectConfig에 등록한 SpringExtension이 처리한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class, JpaConfig::class)
class MemberRepositoryTest(
    memberRepository: MemberRepository,
    em: TestEntityManager,
) : DescribeSpec({
        val encoder = StubPasswordEncoder()

        fun aMember(
            username: String = "Alice",
            nickname: String = "앨리스",
            rawPassword: String = "password",
        ): Member =
            Member.create(
                username = username,
                nickname = nickname,
                hashedPassword = HashedPassword.withEncoder(rawPassword, encoder),
            )

        // 1차 캐시를 비우고 DB에서 다시 읽는다
        fun reload(id: Long): Member {
            em.flush()
            em.clear()
            return em.find(Member::class.java, id).shouldNotBeNull()
        }

        describe("save 메서드는") {
            it("저장하면 id가 채워진다") {
                val saved = memberRepository.save(aMember())

                saved.id.shouldBePositive()
            }
            it("영속성 컨텍스트를 비우고 다시 조회해도 필드가 그대로 복원된다") {
                val saved = memberRepository.save(aMember(username = "Alice", nickname = "앨리스", rawPassword = "secret"))

                val found = reload(saved.id)

                found.username shouldBe "Alice" // 입력한 대소문자 그대로 저장된다
                found.nickname shouldBe "앨리스"
                found.password.value() shouldBe encoder.encode("secret")
            }
            it("대소문자만 다른 username을 저장하면 uk_member_username 위반이 난다") {
                memberRepository.save(aMember(username = "Alice"))

                // IDENTITY 전략이라 save 시점에 INSERT가 바로 실행되고, 리포지토리 프록시가 예외를 변환한다
                val exception =
                    shouldThrow<DataIntegrityViolationException> {
                        memberRepository.save(aMember(username = "alice"))
                    }

                exception.message shouldContain "uk_member_username"
            }
        }
        describe("생성·수정 시각은") {
            it("저장하면 createdAt과 updatedAt이 같은 시각으로 채워진다") {
                val saved = memberRepository.save(aMember())

                saved.createdAt shouldBe saved.updatedAt
            }
            it("다시 조회해도 마이크로초까지 같다") {
                val saved = memberRepository.save(aMember())
                val createdAt = saved.createdAt
                val updatedAt = saved.updatedAt

                val found = reload(saved.id)

                // DATETIME(6) 반올림으로 메모리 값과 DB 값이 어긋나지 않는지 확인한다
                found.createdAt shouldBe createdAt
                found.updatedAt shouldBe updatedAt
            }
        }
    })

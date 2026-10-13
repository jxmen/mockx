package xyz.jxmen.mockx.member

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class MemberTest :
    DescribeSpec({
        val encoder = StubPasswordEncoder()

        describe("create 메서드는") {
            it("username, nickname, hashedPassword가 주어지면 생성된다") {
                val member =
                    Member.create(
                        username = "jxmen",
                        nickname = "Jxmen",
                        hashedPassword = HashedPassword.withEncoder("password", encoder),
                    )

                member.username shouldBe "jxmen"
                member.nickname shouldBe "Jxmen"
                member.password.value() shouldBe "password!@"
            }
        }
        describe("toString 메서드는") {
            it("비밀번호 원문과 해시 값을 노출하지 않는다") {
                val member =
                    Member.create(
                        username = "jxmen",
                        nickname = "Jxmen",
                        hashedPassword = HashedPassword.withEncoder("secret1234", encoder),
                    )

                val result = member.toString()

                result shouldNotContain "secret1234"
                result shouldNotContain "secret1234!@"
            }
        }
    })

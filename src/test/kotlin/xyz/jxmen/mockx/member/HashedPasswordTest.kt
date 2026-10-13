package xyz.jxmen.mockx.member

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldNotContain

class HashedPasswordTest :
    DescribeSpec({
        describe("toString 메서드는") {
            it("비밀번호 원문과 해시 값을 노출하지 않는다") {
                val hashedPassword = HashedPassword.withEncoder("secret1234", StubPasswordEncoder())

                val result = hashedPassword.toString()

                result shouldNotContain "secret1234"
                result shouldNotContain "secret1234!@"
                result shouldBeEqual "****"
            }
        }
    })

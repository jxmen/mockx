package xyz.jxmen.mockx.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withIts
import io.kotest.matchers.shouldBe

class LuhnTest: DescribeSpec({
    describe("computeLastDigit 메서드는") {
        withIts(
            nameFn = { (payload, expected) -> "${payload}가 주어지면 $expected 리턴" },
            "7992739871" to 3,
            "0" to 0,
            "1" to 8,
            "12345678901" to 5,
            "123456789012" to 8,
            "999999999999" to 2,
            "000000000000" to 0,
            "0123456789" to 7,
        ) { (payload, expected) ->
            val result = Luhn.computeLastDigit(payload)

            result shouldBe expected
        }
        context("ASCII 숫자(0-9)로만 이루어진 문자열이 아니면") {
            withIts(
                nameFn = { (description, input) -> "$description('$input')는 예외를 던진다" },
                "빈 문자열" to "",
                "공백" to " ",
                "앞뒤 공백" to " 123 ",
                "중간 공백" to "1 2 3",
                "영문자" to "12a4",
                "하이픈" to "123-456",
                "음수 부호" to "-123",
                "전각 숫자" to "１２３",
                "아랍-인도 숫자" to "١٢٣",
            ) { (_, input) ->
                shouldThrow<IllegalArgumentException> { Luhn.computeLastDigit(input) }
            }
        }
    }
    describe("isValid 메서드는") {
        context("검증숫자가 올바르면") {
            it("true를 리턴한다") {
                Luhn.isValid("79927398713") shouldBe true
            }
        }
        context("검증숫자가 올바르지 않으면") {
            it("false를 리턴한다") {
                Luhn.isValid("79927398710") shouldBe false
            }
        }
        context("올바르지 않은 입력이 주어지면") {
            withIts(
                nameFn = { (description, input) -> "$description('$input')는 예외를 던진다" },
                "마지막 자리만 전각 숫자" to "7992739871３",
                "빈 문자열" to "",
            ) { (_, input) ->
                shouldThrow<IllegalArgumentException> { Luhn.isValid(input) }
            }
        }
    }
})

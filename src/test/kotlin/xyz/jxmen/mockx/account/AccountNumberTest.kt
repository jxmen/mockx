package xyz.jxmen.mockx.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withIts
import io.kotest.matchers.shouldBe

class AccountNumberTest :
    DescribeSpec({
        describe("issue 메서드는") {
            context("1 ~ 99,999,999 범위의 순번이 주어지면") {
                withIts(
                    nameFn = { (sequence, expected) -> "순번 ${sequence}는 $expected 리턴" },
                    1L to "100000000016", // 최솟값, 0 패딩
                    12345678L to "100123456780", // 검증숫자가 0
                    10000000L to "100100000007",
                    99999999L to "100999999996", // 최댓값
                ) { (sequence, expected) ->
                    val result = AccountNumber.issue(AccountType.Brokerage, sequence)

                    result.number shouldBe expected
                }
            }
            context("범위를 벗어난 순번이 주어지면") {
                withIts(
                    nameFn = { (description, sequence) -> "$description($sequence)는 예외를 던진다" },
                    "0" to 0L,
                    "음수" to -1L,
                    "9자리" to 100_000_000L,
                    "Long 최댓값" to Long.MAX_VALUE,
                ) { (_, sequence) ->
                    shouldThrow<IllegalArgumentException> { AccountNumber.issue(AccountType.Brokerage, sequence) }
                }
            }
        }
    })

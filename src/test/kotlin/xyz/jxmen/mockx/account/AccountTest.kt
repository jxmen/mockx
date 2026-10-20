package xyz.jxmen.mockx.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AccountTest :
    DescribeSpec({
        describe("deposit") {
            context("양수 금액을 입금하면") {
                it("예수금이 그만큼 늘어나고, 늘어난 예수금을 반환한다") {
                    val account = anAccount()

                    account.deposit(Money(10_000)) shouldBe Money(10_000)
                    account.balance shouldBe Money(10_000)
                    account.deposit(Money(10_000)) shouldBe Money(20_000)
                    account.balance shouldBe Money(20_000)
                }
            }
            context("음수나 0원 금액 입금 시") {
                it("예외 발생") {
                    val account = anAccount()

                    shouldThrow<IllegalArgumentException> { account.deposit(Money(0)) }
                    shouldThrow<IllegalArgumentException> { account.deposit(Money(-1)) }
                }
            }
        }
        describe("hold") {
            context("주문가능금액 이하의 금액을 hold하면") {
                it("예수금은 그대로, hold 금액만 증가") {
                    val account = anAccount()

                    account.deposit(Money(10_000))
                    account.hold(Money(5_000))

                    account.availableAmount() shouldBe Money(5_000)
                    account.balance shouldBe Money(10_000)
                    account.holdAmount shouldBe Money(5_000)
                }
            }
            context("주문가능금액과 정확히 같은 금액을 hold하면") {
                it("성공하고, 주문가능금액이 0이 된다") {
                    val account = anAccount()

                    account.deposit(Money(10_000))
                    account.hold(Money(10_000))

                    account.availableAmount() shouldBe Money(0)
                }
            }
            context("주문가능금액보다 큰 금액을 hold하면") {
                it("예외가 발생하고, 예수금과 hold 금액은 그대로다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))

                    shouldThrow<IllegalArgumentException> {
                        account.hold(Money(10_001))
                    }
                    account.balance shouldBe Money(10_000)
                    account.holdAmount shouldBe Money(0)
                }
            }
            context("이미 hold한 금액이 있을 때 추가로 hold하면") {
                it("기존 hold를 뺀 남은 주문가능금액 기준으로 판단한다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))
                    account.hold(Money(5_000))

                    account.hold(Money(3_000))

                    account.holdAmount shouldBe Money(8_000)
                    shouldThrow<IllegalArgumentException> {
                        account.hold(Money(2_001))
                    }
                    account.holdAmount shouldBe Money(8_000)
                }
            }
            context("0원이나 음수 금액을 hold하면") {
                it("예외가 발생하고, hold 금액은 그대로다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))

                    shouldThrow<IllegalArgumentException> { account.hold(Money(0)) }
                    account.holdAmount shouldBe Money(0)
                    shouldThrow<IllegalArgumentException> { account.hold(Money(-1)) }
                    account.holdAmount shouldBe Money(0)
                }
            }
        }
        describe("release") {
            context("hold 금액 이하만큼의 숫자가 주어지면") {
                it("hold된 금액이 그만큼 차감된다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))
                    account.hold(Money(5_000))

                    account.release(Money(1_000))

                    account.holdAmount shouldBe Money(4000)
                    account.availableAmount() shouldBe Money(6_000)
                }
            }
            context("hold 금액만큼의 숫자가 주어지면") {
                it("hold 금액이 0원이 된다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))
                    account.hold(Money(5_000))

                    account.release(Money(5_000))

                    account.holdAmount shouldBe Money(0)
                    account.availableAmount() shouldBe Money(10_000)
                }
            }
            context("hold된 금액이 없을경우") {
                it("예외를 던진다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))

                    shouldThrow<IllegalArgumentException> { account.release(Money(1)) }
                    account.holdAmount shouldBe Money(0)
                }
            }
            context("음수나 0이 주어질 경우") {
                it("예외를 던진다") {
                    val account = anAccount()
                    account.deposit(Money(10_000))

                    shouldThrow<IllegalArgumentException> { account.release(Money(0)) }
                    account.holdAmount shouldBe Money(0)
                    shouldThrow<IllegalArgumentException> { account.release(Money(-1)) }
                    account.holdAmount shouldBe Money(0)
                }
            }
        }
    })

package xyz.jxmen.mockx.ledger

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import xyz.jxmen.mockx.common.money.Money

class LedgerTest :
    DescribeSpec({
        describe("createDeposit") {
            context("기존 잔액이 있는 계좌에 입금하면") {
                it("입금 원장이 만들어진다") {
                    val ledger = Ledger.createDeposit(accountId = 1L, amount = Money(10_000), balanceAfter = Money(30_000))

                    ledger.accountId shouldBe 1L
                    ledger.amount shouldBe Money(10_000)
                    ledger.balanceAfter shouldBe Money(30_000)
                    ledger.type shouldBe LedgerType.DEPOSIT
                }
            }
            context("잔액 0원인 계좌에 첫 입금을 하면 (입금액 == 입금 후 잔액)") {
                it("입금 원장이 만들어진다") {
                    val ledger = Ledger.createDeposit(accountId = 1L, amount = Money(10_000), balanceAfter = Money(10_000))

                    ledger.amount shouldBe Money(10_000)
                    ledger.balanceAfter shouldBe Money(10_000)
                }
            }
            context("0원이나 음수 금액이 주어지면") {
                it("예외가 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        Ledger.createDeposit(accountId = 1L, amount = Money(0), balanceAfter = Money(10_000))
                    }
                    shouldThrow<IllegalArgumentException> {
                        Ledger.createDeposit(accountId = 1L, amount = Money(-1), balanceAfter = Money(10_000))
                    }
                }
            }
            context("입금 후 잔액이 입금액보다 작으면") {
                it("예외가 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        Ledger.createDeposit(accountId = 1L, amount = Money(10_000), balanceAfter = Money(9_999))
                    }
                }
            }
            context("입금 후 잔액이 0원이나 음수이면") {
                it("예외가 발생한다") {
                    shouldThrow<IllegalArgumentException> {
                        Ledger.createDeposit(accountId = 1L, amount = Money(10_000), balanceAfter = Money(0))
                    }
                    shouldThrow<IllegalArgumentException> {
                        Ledger.createDeposit(accountId = 1L, amount = Money(10_000), balanceAfter = Money(-1))
                    }
                }
            }
        }
    })

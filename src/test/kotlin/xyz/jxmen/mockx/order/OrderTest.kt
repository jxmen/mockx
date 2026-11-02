package xyz.jxmen.mockx.order

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import xyz.jxmen.mockx.account.Money
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 주문 접수(매수)·취소 전이와 hold 금액 계산.
 * 기준: docs/db-design-order.md 4장(상태), 5장(canceled_at), 6장(접수), 7장(취소 흐름)
 * - 매수 주문은 BUY·ACCEPTED로 만든다. 지정가·수량은 양수여야 한다(상한은 입력 검증이 맡는다)
 * - 주문이 속한 계좌만 취소할 수 있다. 다른 계좌면 OrderNotFoundException(404 ORDER_NOT_FOUND)으로, 없는 주문과 구분되지 않는다
 *   소유권을 상태보다 먼저 확인한다. 남의 취소된 주문에 409를 주면 그 주문이 있다는 것이 드러난다
 * - ACCEPTED 주문만 취소할 수 있다. 아니면 OrderNotCancelableException(409 ORDER_NOT_CANCELABLE)을 엔티티가 직접 던진다
 *   사용자가 정상적으로 만나는 실패라서 Account.hold의 422처럼 응답 예외로 던진다
 * - hold 금액은 저장하지 않고 지정가 × 수량(센트)으로 계산한다
 * - 취소에 성공하면 풀어야 할 금액을 돌려준다. 서비스는 이 값으로 Account.release를 부르므로, 취소 검사 전에 돈이 풀릴 수 없다
 */
class OrderTest : DescribeSpec({
    val ownerAccountId = 1L
    val otherAccountId = 2L

    describe("createBuy") {
        context("매수 주문을 만들면") {
            it("BUY·ACCEPTED 상태로, 넘긴 값 그대로 만들어진다") {
                // accountId와 stockId를 다르게 둬서 인자가 뒤바뀌면 잡히게 한다
                val order = Order.createBuy(accountId = 1L, stockId = 2L, price = 18_743, quantity = 10)

                order.accountId shouldBe 1L
                order.stockId shouldBe 2L
                order.price shouldBe 18_743L
                order.quantity shouldBe 10L
                order.side shouldBe OrderSide.BUY
                order.status shouldBe OrderStatus.ACCEPTED
                order.canceledAt.shouldBeNull()
            }
        }
        context("지정가와 수량이 모두 최솟값 1이면") {
            it("주문이 만들어진다") {
                val order = Order.createBuy(accountId = 1L, stockId = 1L, price = 1, quantity = 1)

                order.price shouldBe 1L
                order.quantity shouldBe 1L
            }
        }
        context("지정가가 0이나 음수이면") {
            it("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Order.createBuy(accountId = 1L, stockId = 1L, price = 0, quantity = 10)
                }
                shouldThrow<IllegalArgumentException> {
                    Order.createBuy(accountId = 1L, stockId = 1L, price = -1, quantity = 10)
                }
            }
        }
        context("수량이 0이나 음수이면") {
            it("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Order.createBuy(accountId = 1L, stockId = 1L, price = 18_743, quantity = 0)
                }
                shouldThrow<IllegalArgumentException> {
                    Order.createBuy(accountId = 1L, stockId = 1L, price = 18_743, quantity = -1)
                }
            }
        }
    }
    describe("cancel") {
        context("주문이 속한 계좌로 접수된(ACCEPTED) 주문을 취소하면") {
            it("CANCELED가 되고, 취소한 시각이 마이크로초 단위로 기록된다") {
                val order = anOrder(accountId = ownerAccountId)
                order.canceledAt.shouldBeNull()
                // 취소 시각은 마이크로초로 잘려 기록되므로 비교 기준도 자른다
                val before = Instant.now().truncatedTo(ChronoUnit.MICROS)

                order.cancel(ownerAccountId)

                val after = Instant.now()
                order.status shouldBe OrderStatus.CANCELED
                val canceledAt = order.canceledAt.shouldNotBeNull()
                canceledAt shouldBeGreaterThanOrEqualTo before
                canceledAt shouldBeLessThanOrEqualTo after
                canceledAt shouldBe canceledAt.truncatedTo(ChronoUnit.MICROS) // DATETIME(6)에 그대로 저장되는 값
            }
            it("풀어야 할 금액으로 지정가 × 수량을 돌려준다") {
                val order = anOrder(accountId = ownerAccountId, price = 18_743, quantity = 10)

                // 18,743 × 10
                order.cancel(ownerAccountId) shouldBe Money(187_430)
            }
        }
        context("이미 취소된 주문을 다시 취소하면") {
            it("ORDER_NOT_CANCELABLE 예외가 발생하고, 상태와 처음 취소 시각은 그대로다") {
                val order = anOrder(accountId = ownerAccountId)
                order.cancel(ownerAccountId)
                val firstCanceledAt = order.canceledAt
                // 두 번째 취소가 같은 마이크로초에 일어나면 시각을 덮어써도 값이 같아 버그가 가려진다. 시각이 바뀔 때까지 기다린다
                while (Instant.now().truncatedTo(ChronoUnit.MICROS) == firstCanceledAt) Thread.onSpinWait()

                val e =
                    shouldThrow<OrderNotCancelableException> {
                        order.cancel(ownerAccountId)
                    }
                e.code shouldBe "ORDER_NOT_CANCELABLE"
                order.status shouldBe OrderStatus.CANCELED
                order.canceledAt shouldBe firstCanceledAt
            }
        }
        context("다른 계좌의 주문을 취소하면") {
            it("ORDER_NOT_FOUND 예외가 발생하고, 주문은 ACCEPTED 그대로다") {
                val order = anOrder(accountId = ownerAccountId)

                val e =
                    shouldThrow<OrderNotFoundException> {
                        order.cancel(otherAccountId)
                    }
                e.code shouldBe "ORDER_NOT_FOUND"
                order.status shouldBe OrderStatus.ACCEPTED
                order.canceledAt.shouldBeNull()
            }
        }
        context("다른 계좌의 이미 취소된 주문을 취소하면") {
            // 상태를 먼저 보면 409가 나가서, 남의 주문 id가 실제로 있다는 것을 알려 주게 된다
            it("ORDER_NOT_CANCELABLE이 아니라 없는 주문과 같은 ORDER_NOT_FOUND 예외가 발생한다") {
                val order = anOrder(accountId = ownerAccountId)
                order.cancel(ownerAccountId)

                val e =
                    shouldThrow<OrderNotFoundException> {
                        order.cancel(otherAccountId)
                    }
                e.code shouldBe "ORDER_NOT_FOUND"
            }
        }
    }
})

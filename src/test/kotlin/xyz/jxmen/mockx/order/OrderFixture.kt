package xyz.jxmen.mockx.order

/**
 * 테스트용 매수 주문. Order.createBuy로 접수(ACCEPTED) 상태로 만든다. 계좌·종목에 관심 없는 테스트에서 사용한다.
 */
fun anOrder(
    accountId: Long = 1L,
    price: Long = 18_743,
    quantity: Long = 10,
): Order = Order.createBuy(accountId = accountId, stockId = 1L, price = price, quantity = quantity)

package xyz.jxmen.mockx.order

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.jxmen.mockx.account.AccountNotFoundException
import xyz.jxmen.mockx.account.AccountRepository
import xyz.jxmen.mockx.stock.StockRepository

@Service
class CancelOrderService(
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository
) {

    @Transactional
    fun cancelOrder(command: CancelOrderCommand): CanceledOrder {
        val account = accountRepository.findWithLockByIdAndMemberId(command.accountId, command.memberId)
            ?: throw AccountNotFoundException(command.accountId)
        val order = orderRepository.findByIdOrNull(command.orderId) ?: throw OrderNotFoundException(command.orderId)

        val holdAmount = order.cancel(account.id)
        account.release(holdAmount)

        val stock = checkNotNull(stockRepository.findByIdOrNull(order.stockId)) { "id '${order.stockId}' 주식 찾을 수 없음" }
        return CanceledOrder.of(order, stock.symbol)
    }
}

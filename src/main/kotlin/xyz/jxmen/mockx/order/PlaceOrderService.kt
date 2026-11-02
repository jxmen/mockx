package xyz.jxmen.mockx.order

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.jxmen.mockx.account.AccountNotFoundException
import xyz.jxmen.mockx.account.AccountRepository
import xyz.jxmen.mockx.stock.StockNotFoundException
import xyz.jxmen.mockx.stock.StockRepository

@Service
class PlaceOrderService(
    private val accountRepository: AccountRepository,
    private val stockRepository: StockRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun placeOrder(command: PlaceOrderCommand): PlacedOrder {
        val stock = stockRepository.findBySymbol(command.symbol) ?: throw StockNotFoundException(command.symbol)
        val account = accountRepository.findWithLockByIdAndMemberId(command.accountId, command.memberId)
            ?: throw AccountNotFoundException(command.accountId)

        // NOTE: 현재는 매수 주문만 지원한다.
        account.hold(command.holdAmount())
        val order = orderRepository.save(command.toBuyOrder(stock.id))

        return PlacedOrder.of(order, stock.symbol)
    }
}

package xyz.jxmen.mockx.order

import org.springframework.stereotype.Service
import xyz.jxmen.mockx.account.AccountNotFoundException
import xyz.jxmen.mockx.account.AccountRepository

@Service
class GetOrdersService(
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository
) {
    fun getOrders(command: GetOrdersCommand): List<OrderSummary> {
        val account = accountRepository.findByIdAndMemberId(command.accountId, command.memberId)
            ?: throw AccountNotFoundException(command.accountId)

        return orderRepository.findSummaries(
            accountId = account.id,
            sides = command.sides ?: OrderSide.entries,
            statuses = command.statuses ?: OrderStatus.entries
        )
    }
}

package xyz.jxmen.mockx.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.jxmen.mockx.common.money.Money
import xyz.jxmen.mockx.ledger.Ledger
import xyz.jxmen.mockx.ledger.LedgerRepository

@Service
class DepositService(
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository
) {
    @Transactional
    fun deposit(
        accountId: Long,
        memberId: Long,
        amount: Long,
    ): DepositResponse {
        val account =
            accountRepository.findWithLockByIdAndMemberId(accountId, memberId)
                ?: throw AccountNotFoundException(accountId)

        val balance = account.deposit(Money(amount))
        val ledger = Ledger.createDeposit(accountId, Money(amount), balance)

        ledgerRepository.save(ledger)
        return DepositResponse.of(accountId, Money(amount), balance)
    }
}

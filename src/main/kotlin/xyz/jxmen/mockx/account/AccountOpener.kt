package xyz.jxmen.mockx.account

import org.springframework.stereotype.Service

@Service
class AccountOpener(
    private val accountNumberRepository: AccountNumberRepository,
    private val accountRepository: AccountRepository,
) {
    fun open(memberId: Long): Account {
        val accountNumber = accountNumberRepository.generateAndIncrease()
        val account = Account.create(memberId = memberId, number = accountNumber)

        return accountRepository.save(account)
    }
}

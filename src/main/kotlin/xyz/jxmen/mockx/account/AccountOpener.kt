package xyz.jxmen.mockx.account

import org.springframework.stereotype.Service

@Service
class AccountOpener(
    private val accountNumberGenerator: AccountNumberGenerator,
    private val accountRepository: AccountRepository,
) {
    fun open(memberId: Long): Account {
        val accountNumber = accountNumberGenerator.generate()
        val account = Account.create(memberId = memberId, number = accountNumber)

        TODO()
        return accountRepository.save(account)
    }
}

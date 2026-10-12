package xyz.jxmen.mockx.account

interface AccountNumberGenerator {
    fun generate(type: AccountType): AccountNumber
}

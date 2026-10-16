package xyz.jxmen.mockx.account

interface AccountNumberRepository {
    fun generateAndIncrease(): AccountNumber
}

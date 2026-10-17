package xyz.jxmen.mockx.account

import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByMemberId(memberId: Long): Account?
}

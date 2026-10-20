package xyz.jxmen.mockx.account

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByMemberId(memberId: Long): Account?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByIdAndMemberId(
        accountId: Long,
        memberId: Long,
    ): Account?
}

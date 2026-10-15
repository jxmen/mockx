package xyz.jxmen.mockx.account

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcAccountNumberGenerator(
    private val jdbcTemplate: JdbcTemplate
): AccountNumberGenerator {

    override fun generate(): AccountNumber {
        // TODO: 위탁 계좌 타입으로 조회

        TODO("Not Implemented")
    }
}

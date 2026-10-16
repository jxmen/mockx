package xyz.jxmen.mockx.account

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcAccountNumberRepository(
    private val jdbcTemplate: JdbcTemplate
): AccountNumberRepository {

    override fun generateAndIncrease(): AccountNumber {
        val productCode = AccountType.Brokerage.prefixNum

        val updated =
            jdbcTemplate.update(
          """
                UPDATE account_number_sequence
                    SET next_value = LAST_INSERT_ID(next_value + 1)
                WHERE product_code = ?
                """.trimIndent(),
                productCode,
            )
        check(updated == 1) {"상품코드 $productCode 순번 행이 없습니다" }

        val seq = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        return AccountNumber.issue(AccountType.Brokerage, seq)
    }
}

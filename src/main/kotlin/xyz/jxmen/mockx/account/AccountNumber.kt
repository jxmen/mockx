package xyz.jxmen.mockx.account

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@ConsistentCopyVisibility
@Embeddable
data class AccountNumber private constructor(
    @Column(name = "number", nullable = false, length = 12)
    val number: String,
) {
    fun value(): String = this.number

    init {
        require(number.length == 12) { "계좌번호는 12자리여야 합니다." }
        require(number.all { it in '0'..'9' }) { "계좌번호는 모두 숫자여야 합니다." }
        require(Luhn.isValid(number)) { "계좌번호 마지막 숫자가 올바르지 않습니다." }
    }

    companion object {
        fun issue(
            type: AccountType,
            sequence: Long,
        ): AccountNumber {
            require(sequence > 0) { "계좌번호 sequence는 양수여야 함" }
            require(sequence < 100_000_000) { "sequence는 8자리여야함" }

            val sequenceString = sequence.toString().padStart(8, '0')
            val last = Luhn.computeLastDigit(type.prefixNum + sequenceString)

            return AccountNumber(type.prefixNum + sequenceString + last)
        }
    }
}

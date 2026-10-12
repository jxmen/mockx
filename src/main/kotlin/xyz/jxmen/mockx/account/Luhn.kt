package xyz.jxmen.mockx.account

object Luhn {
    fun computeLastDigit(payload: String): Int {
        require(payload.isNotBlank())
        require(payload.all { it in '0'..'9' })

        val sum =
            payload
                .reversed()
                .map { it.digitToInt() }
                .mapIndexed { index, n -> if (index % 2 == 0) doubled(n) else n }
                .sum()

        return (10 - sum % 10) % 10
    }

    fun isValid(number: String): Boolean {
        require(number.isNotBlank())
        require(number.last() in '0'..'9')

        return computeLastDigit(number.dropLast(1)) == number.last().digitToInt()
    }

    // 2배 한 값이 9 넘으면 9 빼기
    private fun doubled(n: Int): Int = if (n >= 5) (n * 2) - 9 else n * 2
}

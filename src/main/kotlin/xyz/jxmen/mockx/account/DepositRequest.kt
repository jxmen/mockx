package xyz.jxmen.mockx.account

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive

data class DepositRequest(

    @Positive
    @Max(10000)
    val amount: Long,
)

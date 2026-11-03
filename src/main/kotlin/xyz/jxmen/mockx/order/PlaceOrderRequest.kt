package xyz.jxmen.mockx.order

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

data class PlaceOrderRequest(
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z]{1,10}$")
    val symbol: String,

    val side: OrderSide, // NOTE: jackson이 역직렬화 해줌. 숫자는 설정으로 막음.

    @Positive
    @Max(1000000)
    val price: Long,

    @Positive
    @Max(10000)
    val quantity: Long,
) {
    fun toCommand(accountId: Long, memberId: Long): PlaceOrderCommand {
        return PlaceOrderCommand(
            accountId = accountId,
            memberId = memberId,
            symbol = symbol.uppercase(),
            side = side,
            price = price,
            quantity = quantity,
        )
    }
}

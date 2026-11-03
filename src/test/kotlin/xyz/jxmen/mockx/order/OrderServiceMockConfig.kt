package xyz.jxmen.mockx.order

import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import xyz.jxmen.mockx.common.idempotency.IdempotentExecutor

/**
 * OrderController의 컨트롤러 단위 테스트(OrderControllerTest, CancelOrderControllerTest, GetOrdersControllerTest)가 함께 쓰는 서비스 목.
 * 컨트롤러가 서비스를 모두 주입받으므로 어느 테스트든 모두 있어야 컨텍스트가 뜬다.
 * 멱등성 동작(재응답·409·422)은 PlaceOrderIdempotencyE2ETest가 맡는다.
 */
@TestConfiguration
class OrderServiceMockConfig {
    @Bean
    fun placeOrderService(): PlaceOrderService = mockk()

    @Bean
    fun cancelOrderService(): CancelOrderService = mockk()

    @Bean
    fun getOrdersService(): GetOrdersService = mockk()

    // 목이 아니라 진짜 빈을 쓴다. DB 없이 메모리에서만 돌고, 요청마다 새 키를 쓰므로 action을 그대로 실행한다.
    // 목으로 두면 action을 실행해 주는 stub을 따로 짜야 해서 실제 구현을 테스트에 다시 쓰는 꼴이 된다
    @Bean
    fun idempotentExecutor(): IdempotentExecutor = IdempotentExecutor()
}

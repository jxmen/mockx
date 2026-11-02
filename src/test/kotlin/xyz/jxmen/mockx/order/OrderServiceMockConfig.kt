package xyz.jxmen.mockx.order

import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * OrderController의 컨트롤러 단위 테스트(OrderControllerTest, CancelOrderControllerTest, GetOrdersControllerTest)가 함께 쓰는 서비스 목.
 * 컨트롤러가 서비스를 모두 주입받으므로 어느 테스트든 모두 있어야 컨텍스트가 뜬다.
 */
@TestConfiguration
class OrderServiceMockConfig {
    @Bean
    fun placeOrderService(): PlaceOrderService = mockk()

    @Bean
    fun cancelOrderService(): CancelOrderService = mockk()

    @Bean
    fun getOrdersService(): GetOrdersService = mockk()
}

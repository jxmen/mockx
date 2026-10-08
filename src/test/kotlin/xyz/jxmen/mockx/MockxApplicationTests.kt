package xyz.jxmen.mockx

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MockxApplicationTests {

    @Test
    fun contextLoads() {
    }
}

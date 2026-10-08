package xyz.jxmen.mockx

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HelloController::class)
class HelloControllerTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `hello 요청 시 인사 메시지를 반환한다`() {
        mockMvc.get("/hello")
            .andExpect {
                status { isOk() }
                jsonPath("$.message") { value("Hello, Mock Exchange!") }
            }
    }
}

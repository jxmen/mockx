package xyz.jxmen.mockx.common.jpa

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Configuration
class JpaConfig {
    /**
     * DATETIME(6)은 마이크로초까지만 저장하고 넘는 자릿수는 반올림한다.
     * 메모리 값과 DB 값이 어긋나지 않도록 미리 마이크로초로 자른다.
     */
    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS)) }
}

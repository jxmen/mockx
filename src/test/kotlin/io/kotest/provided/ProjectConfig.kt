package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

/**
 * Kotest 전역 설정. Kotest 6은 이 패키지·클래스 이름(io.kotest.provided.ProjectConfig)을 찾아 자동으로 적용한다.
 * - SpringExtension: Spring 테스트 컨텍스트(@DataJpaTest 등)를 Kotest 스펙에 연결하고, 생성자 주입과 테스트별 트랜잭션 롤백을 지원한다.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(SpringExtension())
}

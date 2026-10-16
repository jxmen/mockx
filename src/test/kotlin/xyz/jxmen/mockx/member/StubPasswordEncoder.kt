package xyz.jxmen.mockx.member

import org.springframework.security.crypto.password.PasswordEncoder

// 해시 비용 없이 결과를 예측할 수 있는 테스트용 인코더. null 처리는 Spring PasswordEncoder 계약을 따른다
class StubPasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String? = rawPassword?.let { "$it!@" }

    override fun matches(
        rawPassword: CharSequence?,
        encodedPassword: String?,
    ): Boolean = rawPassword != null && !encodedPassword.isNullOrEmpty() && encodedPassword == encode(rawPassword)
}

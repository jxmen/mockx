package xyz.jxmen.mockx.member

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @Size(min = 2, max = 10)
    @NotBlank
    val nickname: String,
    @Size(min = 2, max = 10)
    @NotBlank
    val username: String,
    @Size(max = 255)
    val password: String,
    @Size(max = 255)
    val passwordConfirm: String,
)

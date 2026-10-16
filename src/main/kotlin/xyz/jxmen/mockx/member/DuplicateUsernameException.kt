package xyz.jxmen.mockx.member

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class DuplicateUsernameException(username: String): ApiException(
    status = HttpStatus.CONFLICT,
    code = "USERNAME_DUPLICATED",
    message = "이름 '$username'은 이미 존재합니다.",
    field = "username"
)

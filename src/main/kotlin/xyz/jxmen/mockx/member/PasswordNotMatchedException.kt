package xyz.jxmen.mockx.member

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class PasswordNotMatchedException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "PASSWORD_NOT_MATCHED",
        field = "passwordConfirm",
        message = "비밀번호 확인이 일치하지 않습니다.",
    )

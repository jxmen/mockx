package xyz.jxmen.mockx.member

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class MemberNotFoundException :
    ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "MEMBER_NOT_FOUND",
        message = "회원을 찾을 수 없습니다.",
    )

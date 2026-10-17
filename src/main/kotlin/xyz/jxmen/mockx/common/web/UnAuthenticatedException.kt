package xyz.jxmen.mockx.common.web

import org.springframework.http.HttpStatus

class UnAuthenticatedException: ApiException(
    status = HttpStatus.UNAUTHORIZED,
    code = "UNAUTHENTICATED",
    message = "UnAuthenticated",
)

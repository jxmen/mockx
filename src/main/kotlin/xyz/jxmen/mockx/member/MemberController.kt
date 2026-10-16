package xyz.jxmen.mockx.member

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val signupService: SignupService,
) {
    @PostMapping
    fun signUp(
        @RequestBody @Valid request: SignupRequest,
    ): ResponseEntity<SignupResponse> {
        if (request.password != request.passwordConfirm) throw PasswordNotMatchedException()

        val response = signupService.signup(request)

        return ResponseEntity
            .created(
                URI.create("/api/members/${response.memberId}"),
            ).body(response)
    }
}

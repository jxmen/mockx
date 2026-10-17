package xyz.jxmen.mockx.member

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val signupService: SignupService,
    private val getMyInfoService: GetMyInfoService,
) {
    @PostMapping
    fun signUp(
        @RequestBody @Valid request: SignupRequest,
    ): ResponseEntity<SignupResponse> {
        if (request.password != request.passwordConfirm) throw PasswordNotMatchedException()

        val response = signupService.signup(request)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/me")
    fun me(@CurrentMember memberId: Long): ResponseEntity<MyInfoResponse> {
        val response = getMyInfoService.getMyInfo(memberId)

        return ResponseEntity.ok(response)
    }
}

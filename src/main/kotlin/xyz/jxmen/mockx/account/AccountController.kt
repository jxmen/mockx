package xyz.jxmen.mockx.account

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.jxmen.mockx.member.CurrentMember

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val depositService: DepositService
) {

    @PostMapping("/{accountId}/deposit")
    fun deposit(
        @PathVariable accountId: Long,
        @CurrentMember memberId: Long,
        @RequestBody @Valid request: DepositRequest
    ): ResponseEntity<DepositResponse> {
        val response = depositService.deposit(accountId, memberId, request.amount)

        return ResponseEntity.ok(response)
    }
}

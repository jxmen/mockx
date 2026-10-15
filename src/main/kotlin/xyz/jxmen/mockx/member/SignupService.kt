package xyz.jxmen.mockx.member

import org.springframework.stereotype.Service
import xyz.jxmen.mockx.account.AccountOpener

class SignupRequest(
    val nickname: String,
    val username: String,
    val password: String,
    val passwordConfirm: String,
)

class SignupResult

@Service
class SignupService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: IPasswordEncoder,
    private val accountOpener: AccountOpener,
) {
    fun signup(request: SignupRequest): SignupResult {
        // TODO: 해시를 먼저 하고, transactionTemplate로 트랜잭션 범위 지정
        val hashedPassword = HashedPassword.withEncoder(request.password, passwordEncoder)

        // TODO: transactionTemplate
        // TODO: Propagation.MANDATORY

        // TODO: 중복된 username 비허용 - USERNAME_DUPLICATED
        val member =
            Member.create(
                username = request.username,
                nickname = request.nickname,
                hashedPassword = hashedPassword,
            )
        val saved = memberRepository.save(member)
        accountOpener.open(saved.id)

        TODO("Not yet implemented")
    }
}

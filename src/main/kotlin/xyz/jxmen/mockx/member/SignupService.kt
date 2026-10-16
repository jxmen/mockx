package xyz.jxmen.mockx.member

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import xyz.jxmen.mockx.account.AccountOpener

@Service
class SignupService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val accountOpener: AccountOpener,
    private val transactionTemplate: TransactionTemplate,
) {
    fun signup(request: SignupRequest): SignupResponse {
        val hashedPassword = HashedPassword.withEncoder(request.password, passwordEncoder)

        return transactionTemplate.execute {
            val member =
                Member.create(
                    username = request.username,
                    nickname = request.nickname,
                    hashedPassword = hashedPassword,
                )
            val savedMember =
                try {
                    memberRepository.save(member)
                } catch (e: DataIntegrityViolationException) {
                    val cve = e.cause as? ConstraintViolationException
                    if (cve?.constraintName == "member.uk_member_username") {
                        throw DuplicateUsernameException(request.username)
                    } else {
                        throw e
                    }
                }
            val account = accountOpener.open(savedMember.id)

            SignupResponse.of(savedMember, account)
        }
    }
}

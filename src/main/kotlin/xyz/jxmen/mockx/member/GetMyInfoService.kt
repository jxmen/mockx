package xyz.jxmen.mockx.member

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.jxmen.mockx.account.AccountRepository

@Service
@Transactional(readOnly = true)
class GetMyInfoService(
    private val memberRepository: MemberRepository,
    private val accountRepository: AccountRepository,
) {
    fun getMyInfo(memberId: Long): MyInfoResponse {
        val member = memberRepository.findByIdOrNull(memberId) ?: throw MemberNotFoundException()
        // 가입 시 회원과 계좌를 한 트랜잭션에서 만드므로, 계좌가 없으면 404가 아니라 불변식 위반(500)이다
        val account = checkNotNull(accountRepository.findByMemberId(memberId)) { "회원 ${memberId}의 계좌가 없습니다." }

        return MyInfoResponse.of(member, account)
    }
}

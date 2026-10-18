package xyz.jxmen.mockx.account

/**
 * 테스트용 계좌. 계좌번호에 관심 없는 테스트에서 사용한다.
 */
fun anAccount(memberId: Long = 1L): Account = Account.create(memberId, AccountNumber.issue(AccountType.BROKERAGE, 1))

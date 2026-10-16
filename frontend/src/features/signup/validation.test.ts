import { describe, expect, it } from 'vitest'
import { containsHangul, toSignupRequest, validateSignup, type SignupForm } from './validation'

const valid: SignupForm = {
  username: 'Alice',
  nickname: '앨리스',
  password: 'pass1234',
  passwordConfirm: 'pass1234',
}

describe('toSignupRequest', () => {
  it('username, nickname의 앞뒤 공백을 지운다', () => {
    const request = toSignupRequest({ ...valid, username: '  Alice ', nickname: ' 앨리스  ' })

    expect(request.username).toBe('Alice')
    expect(request.nickname).toBe('앨리스')
  })

  it('NFD로 분해된 한글 닉네임을 NFC로 합친다', () => {
    const request = toSignupRequest({ ...valid, nickname: '앨리스'.normalize('NFD') })

    expect(request.nickname).toBe('앨리스')
    expect(request.nickname.length).toBe(3)
  })

  it('password는 공백을 포함해 그대로 둔다', () => {
    const request = toSignupRequest({ ...valid, password: ' pa ss ', passwordConfirm: ' pa ss ' })

    expect(request.password).toBe(' pa ss ')
  })
})

describe('validateSignup', () => {
  it('올바른 입력이면 에러가 없다', () => {
    expect(validateSignup(valid)).toEqual({})
  })

  it.each([
    ['a', true],
    ['ab', false],
    ['abcdefghij', false],
    ['abcdefghijk', true],
    ['__', false],
    ['a-b_C9', false],
    ['al ice', true],
    ['앨리스', true],
    ['alice!', true],
  ])('username "%s" → 에러 %s', (username, hasError) => {
    expect(validateSignup({ ...valid, username }).username !== undefined).toBe(hasError)
  })

  it.each([
    ['앨', true],
    ['앨리', false],
    ['가나다라마바사아자차', false],
    ['가나다라마바사아자차카', true],
    ['Alice1', false],
    ['ㅇㅇ', true],
    ['앨 리', true],
    ['앨리스!', true],
  ])('nickname "%s" → 에러 %s', (nickname, hasError) => {
    expect(validateSignup({ ...valid, nickname }).nickname !== undefined).toBe(hasError)
  })

  it.each([
    ['abc', true],
    ['abcd', false],
    ['a'.repeat(20), false],
    ['a'.repeat(21), true],
    ['    ', false],
    ['비밀번호1234', true],
    ['pass\t1234', true],
  ])('password "%s" → 에러 %s', (password, hasError) => {
    expect(validateSignup({ ...valid, password, passwordConfirm: password }).password !== undefined).toBe(hasError)
  })

  it('비밀번호 확인이 다르면 passwordConfirm 에러', () => {
    expect(validateSignup({ ...valid, passwordConfirm: 'pass12345' }).passwordConfirm).toBeDefined()
  })

  it('비밀번호 확인이 비어 있으면 passwordConfirm 에러', () => {
    expect(validateSignup({ ...valid, passwordConfirm: '' }).passwordConfirm).toBeDefined()
  })

  it('앞뒤 공백은 지운 뒤 검사한다', () => {
    expect(validateSignup({ ...valid, username: ' Alice ' }).username).toBeUndefined()
  })
})

describe('containsHangul', () => {
  it.each([
    ['ㅂㅈㄷㄱ', true],
    ['앨리스', true],
    ['qwer', false],
    ['', false],
  ])('"%s" → %s', (value, expected) => {
    expect(containsHangul(value)).toBe(expected)
  })
})

const SITE_NAME = 'mockx'

/**
 * 브라우저 탭 제목. (예: "회원가입" → "mockx | 회원가입")
 * 화면에서 <title>{pageTitle('회원가입')}</title>로 쓰면 React 19가 <head>로 올려 준다. 화면이 없으면 index.html의 "mockx"가 남는다.
 */
export function pageTitle(page: string): string {
  return `${SITE_NAME} | ${page}`
}

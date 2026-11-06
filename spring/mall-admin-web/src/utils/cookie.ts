import Cookies from 'js-cookie'

// 설정cookie
export function setCookie(key: string, value: string, expires: number) {
  return Cookies.set(key, value, { expires: expires })
}

// 조회cookie
export function getCookie(key: string) {
  return Cookies.get(key)
}

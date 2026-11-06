import type { UmsMenu } from './menu'

/** 로그인 요청 파라미터 */
export type LoginParam = {
  /** 사용자 이름 */
  username: string
  /** 비밀번호 */
  password: string
}

/** 로그인 반환 결과 */
export type LoginResult = {
  /** token접두사 */
  tokenHead: string
  /** 로그인의token */
  token: string
}

/** 사용자 정보 결과 래핑 */
export type UserInfoResult = {
  /** 사용자 이름 */
  username: string
  /** 프로필 이미지 */
  icon: string
  /** 메뉴 */
  menus: UmsMenu[]
  /** 역할 */
  roles: []
}

/** 사용자 정보(store에 저장된 것) */
export type UserInfo = Pick<UserInfoResult, 'username' | 'menus' | 'roles'> & {
  /** 비밀번호 */
  password: string
  /** 로그인token */
  token: string
  /** 프로필 이미지 */
  avatar
}

/** 관리자 정보 */
export type UmsAdmin = {
  /** ID */
  id?: number
  /** 사용자 이름 */
  username: string
  /** 비밀번호 */
  password: string
  /** 프로필 이미지 */
  icon?: string
  /** 이메일 */
  email?: string
  /** 닉네임 */
  nickName?: string
  /** 메모 정보 */
  note?: string
  /** 생성 시간 */
  createTime?: string
  /** 마지막 로그인 시간 */
  loginTime?: string
  /** 계정 사용 상태:0->사용 안 함;1->사용 */
  status: number
}

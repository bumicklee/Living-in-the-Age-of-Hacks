import { defineStore } from 'pinia'
import { getAdminInfoAPI, adminLoginAPI, adminLogoutAPI } from '@/apis/admin'
import { ref } from 'vue'
import type { LoginParam, UserInfo } from '@/types/admin'

export const useUserStore = defineStore(
  'user',
  () => {
    // 사용자 정보
    const userInfo = ref<UserInfo>({
      username: '',
      password: '',
      avatar: '',
      roles: [],
      token: '',
      menus: [],
    })

    // 사용자 로그인
    const userLogin = async (loginParam: LoginParam) => {
      const res = await adminLoginAPI(loginParam)
      const tokenStr = res.data.tokenHead + res.data.token
      userInfo.value.token = tokenStr
      userInfo.value.username = loginParam.username
      userInfo.value.password = loginParam.password
      await getUserInfo()
    }

    // 사용자 정보 조회
    const getUserInfo = async () => {
      const res = await getAdminInfoAPI()
      if (res.data.roles && res.data.roles.length > 0) {
        // 반환된 roles가 비어 있지 않은 배열인지 검증
        userInfo.value.roles = res.data.roles
      } else {
        throw new Error('이 사용자에게 아직 역할이 할당되지 않았습니다. 먼저 역할을 할당해 주세요!')
      }
      userInfo.value.menus = res.data.menus
      userInfo.value.avatar = res.data.icon
    }

    // 사용자 로그아웃
    const userLogout = async () => {
      await adminLogoutAPI()
      userInfo.value.token = ''
      userInfo.value.roles = []
    }

    // 프런트엔드 로그아웃
    const fedLogout = () => {
      userInfo.value.token = ''
    }

    return {
      userInfo,
      userLogin,
      getUserInfo,
      userLogout,
      fedLogout,
    }
  },
  {
    // 영속화 설정
    persist: true,
  },
)

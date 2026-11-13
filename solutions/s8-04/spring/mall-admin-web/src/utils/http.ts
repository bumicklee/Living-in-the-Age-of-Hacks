import { useUserStore } from '@/stores/user'
import type { CommonResult } from '@/types/common'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// axios 인스턴스 생성
const http = axios.create({
  baseURL: import.meta.env.VITE_BASE_SERVER_URL,
  timeout: 5000,
})

// axios요청 인터셉터
http.interceptors.request.use(
  config => {
    //pinia에서 조회token
    const userStore = useUserStore()
    const token = userStore.userInfo.token
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  e => Promise.reject(e),
)

// axios응답 인터셉터
http.interceptors.response.use(
  response => {
    const res: CommonResult<unknown> = response.data
    if (res.code !== 200) {
      // code200이 아니면 예외를 던지므로 여기서 안내 메시지를 일괄 처리
      ElMessage({
        message: res.message,
        type: 'error',
        duration: 3 * 1000,
      })
      // 401:미로그인;
      if (res.code === 401) {
        ElMessageBox.confirm('로그아웃되었습니다. 취소하고 이 페이지에 계속 머물거나 다시 로그인할 수 있습니다', '로그아웃 확인', {
          confirmButtonText: '다시 로그인',
          cancelButtonText: '취소',
          type: 'warning',
        }).then(() => {
          const userStore = useUserStore()
          userStore.fedLogout()
          // vue-router 객체를 다시 인스턴스화하기 위해 방지bug
          location.reload()
        })
      }
      return Promise.reject('error')
    } else {
      // 응답 JSON의 data 속성을 반환하며 message와code
      return response.data
    }
  },
  error => {
    // 다온마켓 8-4(F32): 서버가 이제 로그인 필요를 HTTP 401, 권한 없음을 HTTP 403으로 보낸다(본문 모양은 그대로).
    // axios는 2xx가 아니면 여기로 오므로, 위와 같은 안내(다시 로그인)를 여기서도 한다
    const res: CommonResult<unknown> | undefined = error.response?.data
    const status: number | undefined = error.response?.status
    if (status === 401 || status === 403) {
      ElMessage({
        message: res?.message || (status === 401 ? '로그인이 필요합니다' : '권한이 없습니다'),
        type: 'error',
        duration: 3 * 1000,
      })
      if (status === 401) {
        ElMessageBox.confirm('로그아웃되었습니다. 취소하고 이 페이지에 계속 머물거나 다시 로그인할 수 있습니다', '로그아웃 확인', {
          confirmButtonText: '다시 로그인',
          cancelButtonText: '취소',
          type: 'warning',
        }).then(() => {
          const userStore = useUserStore()
          userStore.fedLogout()
          location.reload()
        })
      }
      return Promise.reject('error')
    }
    // 요청 예외 전역 처리
    console.log('error' + error)
    ElMessage({
      message: error.message,
      type: 'error',
      duration: 3 * 1000,
    })
    return Promise.reject(error)
  },
)

export default http

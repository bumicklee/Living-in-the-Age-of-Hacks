import router from '@/router/index'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import usePermissionStore from '@/stores/permission'

// 로그인 없이 접근 가능한 화이트리스트 경로
const whiteList = ['/login']
// 라우트 전역 전처리 가드 함수 설정(라우트 이동 때마다 실행됨)
router.beforeEach((to, from, next) => {
  NProgress.start()
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  if (userStore.userInfo.token) {
    if (to.path === '/login') {
      // 로그인 상태에서 login에 접근하면 바로 홈으로 이동
      next({ path: '/' })
      NProgress.done()
    } else {
      if (permissionStore.addRouters.length === 0) {
        // 로그인 상태에서 동적 라우트가 없으면 menus로 동적 라우트 생성
        permissionStore.generateRoutes({
          menus: userStore.userInfo.menus,
          username: userStore.userInfo.username,
        })
        permissionStore.addRouters.forEach(route => {
          router.addRoute(route)
        })
        next({ ...to, replace: true })
      } else {
        next()
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      // 미로그인 상태에서 화이트리스트 경로 허용
      next()
    } else {
      // 미로그인 상태에서 화이트리스트가 아닌 경로는 로그인 페이지로 이동
      next('/login')
      NProgress.done()
    }
  }
})

// 라우트 후처리 가드 함수 설정
router.afterEach(() => {
  NProgress.done()
})

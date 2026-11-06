import { defineStore } from 'pinia'
import { shallowRef } from 'vue'
import { asyncRouterMap, constantRouterMap } from '@/router/index'
import type { UmsMenu } from '@/types/menu'
import type { RouteRecordExt } from '@/types/router'

//해당 메뉴에 접근할 권한이 있는지 판단
function hasPermission(menus: UmsMenu[], route: RouteRecordExt) {
  if (route.name) {
    const currMenu = getMenu(route.name as string, menus)
    if (currMenu != null) {
      //메뉴의 제목, 아이콘 및 표시 여부 설정
      if (currMenu.title != null && currMenu.title !== '') {
        route.meta!.title = currMenu.title
      }
      if (currMenu.icon != null && currMenu.title !== '') {
        route.meta!.icon = currMenu.icon
      }
      if (currMenu.hidden != null) {
        route.hidden = currMenu.hidden !== 0
      }
      if (currMenu.sort != null) {
        route.sort = currMenu.sort
      }
      return true
    } else {
      route.sort = 0
      if (route.hidden !== undefined && route.hidden === true) {
        route.sort = -1
        return true
      } else {
        return false
      }
    }
  } else {
    return true
  }
}

// 라우트 이름으로 메뉴 조회
function getMenu(name: string, menus: UmsMenu[]) {
  return menus.find(menu => name === menu.name) || null
}

// 메뉴 정렬
function sortRouters(accessedRouters: RouteRecordExt[]) {
  accessedRouters.forEach(router => {
    if (router.children && router.children.length > 0) {
      router.children.sort((a, b) => compare(a, b))
    }
  })
  accessedRouters.sort((a, b) => compare(a, b))
}

// 내림차순 비교 함수
function compare(a: RouteRecordExt, b: RouteRecordExt) {
  if (a.sort && b.sort) {
    return b.sort - a.sort
  } else {
    return 0
  }
}

export const usePermissionStore = defineStore('permission', () => {
  // 전체 라우트, 정적 라우트 + 동적 라우트
  const routers = shallowRef(constantRouterMap)
  // 접근 권한이 있는 동적 라우트
  const addRouters = shallowRef<RouteRecordExt[]>([])
  // 테스트 모드 여부
  const testMode = false

  // 접근 가능한 라우트 테이블 생성
  const generateRoutes = (data: { menus: UmsMenu[]; username: string }) => {
    const { menus, username } = data
    const accessedRouters = asyncRouterMap.filter(v => {
      // 테스트 모드에서는 admin 계정이 모든 메뉴를 바로 반환
      if (testMode && username === 'admin') return true
      if (hasPermission(menus, v)) {
        if (v.children && v.children.length > 0) {
          v.children = v.children.filter(child => {
            if (hasPermission(menus, child)) {
              return child
            }
            return false
          })
          return v
        } else {
          return v
        }
      }
      return false
    })
    //메뉴 정렬
    sortRouters(accessedRouters)
    addRouters.value = accessedRouters
    routers.value = constantRouterMap.concat(accessedRouters)
  }

  return {
    routers,
    addRouters,
    generateRoutes,
  }
})

// 기본 내보내기로 호환성 유지
export default usePermissionStore

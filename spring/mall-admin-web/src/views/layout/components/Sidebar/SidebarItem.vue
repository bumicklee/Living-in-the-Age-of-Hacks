<script lang="ts" setup>
import type { RouteRecordExt } from '@/types/router'
import { computed, type PropType } from 'vue'

// 컴포넌트 이름 정의
defineOptions({
  name: 'SidebarItem'
})

// 정의props
const props = defineProps({
  // 메뉴 라우트 생성
  routes: {
    type: Array as PropType<RouteRecordExt[]>
  },
  // 하위 메뉴가 하나뿐인 1차 메뉴 스타일 제어
  isNest: {
    type: Boolean,
    default: false
  }
})

// 표시할 라우트 필터링
const filteredRoutes = computed(() => {
  return props.routes!.filter(item => !item.hidden && item.children)
})

// 표시할 하위 라우트 필터링
const getFilteredChildren = (children: RouteRecordExt[]) => {
  return children.filter(child => !child.hidden)
}

// 라우트 아래에 하위 라우트가 하나뿐인지 판단
const hasOneShowingChildren = (children: RouteRecordExt[]) => {
  const showingChildren = children.filter(item => {
    return !item.hidden
  })
  if (showingChildren.length === 1) {
    return true
  }
  return false
}
</script>

<template>
  <div class="menu-wrapper">
    <template v-for="item in filteredRoutes">
      <!-- 하위 메뉴가 하나뿐인 1단계 메뉴 -->
      <router-link
        v-if="item.children && hasOneShowingChildren(item.children) && !item.children[0]!.children && !item.alwaysShow"
        :to="item.path + '/' + item.children[0]!.path" :key="item.children[0]!.name">
        <el-menu-item :index="item.path + '/' + item.children[0]!.path"
          :class="{ 'submenu-title-noDropdown': !isNest }">
          <svg-icon v-if="item.children[0]!.meta && item.children[0]!.meta.icon"
            :icon-class="item.children[0]!.meta.icon">
          </svg-icon>
          <template #title>
            <span v-if="item.children[0]!.meta && item.children[0]!.meta.title">{{ item.children[0]!.meta.title
              }}</span>
          </template>
        </el-menu-item>
      </router-link>
      <!-- 하위 메뉴가 여러 개인 1차 메뉴 -->
      <el-sub-menu v-else :index="item.name as string || item.path" :key="item.name">
        <!-- 1단계 메뉴 -->
        <template #title>
          <svg-icon v-if="item.meta && item.meta.icon" :icon-class="item.meta.icon"></svg-icon>
          <span v-if="item.meta && item.meta.title">{{ item.meta.title }}</span>
        </template>
        <!-- 하위 메뉴 -->
        <template v-for="child in getFilteredChildren(item.children!)">
          <sidebar-item :is-nest="true" class="nest-menu" v-if="child.children && child.children.length > 0"
            :routes="[child]" :key="child.path"></sidebar-item>
          <!-- 외부 링크 기능이 있는 하위 메뉴 -->
          <a v-else-if="child.path.startsWith('http')" v-bind:href="child.path" target="_blank" :key="child.name">
            <el-menu-item :index="item.path + '/' + child.path">
              <svg-icon v-if="child.meta && child.meta.icon" :icon-class="child.meta.icon"></svg-icon>
              <template #title>
                <span v-if="child.meta && child.meta.title">{{ child.meta.title }}</span>
              </template>
            </el-menu-item>
          </a>
          <!-- 일반 하위 메뉴 -->
          <router-link v-else :to="item.path + '/' + child.path" :key="'route-' + (child.name as string)">
            <el-menu-item :index="item.path + '/' + child.path">
              <svg-icon v-if="child.meta && child.meta.icon" :icon-class="child.meta.icon"></svg-icon>
              <template #title>
                <span v-if="child.meta && child.meta.title">{{ child.meta.title }}</span>
              </template>
            </el-menu-item>
          </router-link>
        </template>
      </el-sub-menu>

    </template>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useRoute, type RouteLocationMatched } from 'vue-router'
import { ElBreadcrumb, ElBreadcrumbItem } from 'element-plus'

// 컴포넌트 이름 정의
defineOptions({
  name: 'Breadcrumb'
})

// Element Plus 컴포넌트 명시적 등록
defineExpose({
  ElBreadcrumb,
  ElBreadcrumbItem
})

// 반응형 데이터
const levelList = ref<RouteLocationMatched[]>([])
const route = useRoute()

// 계산된 속성, 제목이 있는 브레드크럼 항목만 반환
const visibleLevelList = computed(() => {
  return levelList.value.filter(item => item.meta?.title)
})

// 브레드크럼 경로 조회
const getBreadcrumb = () => {
  let matched = route.matched.filter(item => item.name)
  const first = matched[0]
  if (first && first.name !== 'home') {
    const homeRoute: RouteLocationMatched = { ...first, path: '/home', meta: { title: '홈' }, name: 'home' }
    matched = [homeRoute].concat(matched)
  }
  levelList.value = matched
}

// 라우트 변경 감시
watch(route, () => {
  getBreadcrumb()
}, { immediate: true })

// 초기화
getBreadcrumb()
</script>

<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, index) in visibleLevelList" :key="item.path">
        <span v-if="item.redirect === 'noredirect' || index == visibleLevelList.length - 1" class="no-redirect">{{
          item.meta.title
          }}</span>
        <router-link v-else :to="typeof item.redirect === 'string' ? item.redirect : item.path">{{ item.meta.title
          }}</router-link>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<style lang="scss" scoped>
.app-breadcrumb.el-breadcrumb {
  display: inline-block;
  font-size: 14px;
  line-height: 50px;
  margin-left: 10px;

  .no-redirect {
    color: #97a8be;
    cursor: text;
  }
}
</style>

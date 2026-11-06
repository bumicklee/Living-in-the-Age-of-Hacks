<script lang="ts" setup>
import { ref } from 'vue'

// 컴포넌트 이름 정의
defineOptions({
  name: 'ScrollBar'
})

const delta = 15

// 반응형 데이터
const top = ref(0)
const scrollContainer = ref<HTMLDivElement>()
const scrollWrapper = ref<HTMLDivElement>()

// 스크롤 이벤트 처리
const handleScroll = (e: WheelEvent) => {
  const eventDelta = -e.deltaY * 3
  const $container = scrollContainer.value
  if (!$container) return
  const $containerHeight = $container.offsetHeight
  const $wrapper = scrollWrapper.value
  if (!$wrapper) return
  const $wrapperHeight = $wrapper.offsetHeight

  if (eventDelta > 0) {
    top.value = Math.min(0, top.value + eventDelta)
  } else {
    if ($containerHeight - delta < $wrapperHeight) {
      if (top.value < -($wrapperHeight - $containerHeight + delta)) {
        // 이미 경계에 도달했으면 다시 할당할 필요 없음
      } else {
        top.value = Math.max(top.value + eventDelta, $containerHeight - $wrapperHeight - delta)
      }
    } else {
      top.value = 0
    }
  }
}
</script>

<template>
  <div class="scroll-container" ref="scrollContainer" @wheel.prevent="handleScroll">
    <div class="scroll-wrapper" ref="scrollWrapper" :style="{ top: top + 'px' }">
      <slot></slot>
    </div>
  </div>
</template>

<style rel="stylesheet/scss" lang="scss" scoped>
@use '../../styles/variables.scss' as vars;

.scroll-container {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: vars.$menuBg;

  .scroll-wrapper {
    position: absolute;
    width: 100% !important;
  }
}
</style>

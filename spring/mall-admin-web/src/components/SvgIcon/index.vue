<script lang="ts" setup>
import { computed } from 'vue'

// 컴포넌트 이름 정의
defineOptions({
  name: 'svg-icon'
})

// 정의props
const props = defineProps({
  iconClass: {
    type: String,
    required: true
  },
  className: {
    type: String
  }
})

const iconName = computed(() => {
  return `#icon-${props.iconClass}`
})

const svgClass = computed(() => {
  if (props.className) {
    return 'svg-icon ' + props.className
  } else {
    return 'svg-icon'
  }
})
</script>

<template>
  <svg :class="svgClass" aria-hidden="true">
    <use :xlink:href="iconName"></use>
  </svg>
</template>

<style scoped>
.svg-icon {
  width: 1.2em;
  height: 1.2em;
  vertical-align: -0.18em;
  fill: currentColor;
  overflow: hidden;
}
</style>

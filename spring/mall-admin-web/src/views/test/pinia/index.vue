<script lang="ts" setup>

defineOptions({
  name: 'TestPinia'
})

// 1. use 메서드 import
import { useCounterStore } from '@/stores/counter'
// 2. 메서드를 실행해 store를 얻음, store에는 데이터와 메서드가 있음
const counterStore = useCounterStore()
</script>

<template>
  <button @click="counterStore.increment">
    {{ counterStore.count }}
  </button>
</template>

<style scoped></style>

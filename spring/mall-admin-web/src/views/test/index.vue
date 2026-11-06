<script lang="ts" setup>
import TestPinia from '@/views/test/pinia/index.vue'
import TestElement from '@/views/test/element/index.vue'
import TestAxios from '@/views/test/axios/index.vue'
</script>

<template>
  <div class="vertical-layout">
    <h1>컴포넌트 테스트 페이지</h1>
    <!-- pinia컴포넌트 테스트 -->
    <div>
      <TestPinia></TestPinia>
    </div>
    <!-- element-plus컴포넌트 테스트 -->
    <div>
      <TestElement></TestElement>
    </div>
    <!-- axiso인터페이스 테스트 호출 -->
    <div>
      <TestAxios></TestAxios>
    </div>
  </div>
</template>

<style scoped>
.vertical-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
}
</style>

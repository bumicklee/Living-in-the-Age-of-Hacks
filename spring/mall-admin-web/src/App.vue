<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import ko from 'element-plus/es/locale/lang/ko'
</script>

<template>
  <!-- ConfigProvider로 국제화를 전역 설정 -->
  <el-config-provider :locale="ko">
    <div id="app">
      <router-view />
    </div>
  </el-config-provider>
</template>

<style></style>

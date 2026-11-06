<script lang="ts" setup>
// 컴포넌트 이름 정의
defineOptions({
  name: 'AppMain'
})
</script>

<template>
  <section class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </section>
</template>

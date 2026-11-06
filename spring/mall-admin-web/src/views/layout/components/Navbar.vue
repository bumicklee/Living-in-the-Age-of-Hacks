<script lang="ts" setup>
import { computed } from 'vue'
import Breadcrumb from '@/components/Breadcrumb/index.vue'
import Hamburger from '@/components/Hamburger/index.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { ArrowDown } from '@element-plus/icons-vue'

// 컴포넌트 이름 정의
defineOptions({
  name: 'Navbar'
})

const appStore = useAppStore()
const userStore = useUserStore()

const sidebar = computed(() => appStore.sidebar)
const avatar = computed(() => userStore.userInfo.avatar)

// 사이드바 열기/닫기 처리
const handleToggleSideBar = () => {
  appStore.toggleSideBar()
}

// 사용자 로그아웃 처리
const handleLogout = async () => {
  await userStore.userLogout()
  // vue-router 객체를 다시 인스턴스화하기 위해 방지bug
  location.reload()
}
</script>

<template>
  <el-menu class="navbar" mode="horizontal">
    <hamburger class="hamburger-container" :toggle-click="handleToggleSideBar" :is-active="sidebar.opened"></hamburger>
    <breadcrumb></breadcrumb>
    <el-dropdown class="avatar-container" trigger="click">
      <div class="avatar-wrapper">
        <img class="user-avatar" :src="avatar">
        <el-icon class="el-icon-caret-bottom">
          <arrow-down />
        </el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu class="user-dropdown">
          <router-link class="inlineBlock" to="/">
            <el-dropdown-item>
              홈
            </el-dropdown-item>
          </router-link>
          <el-dropdown-item divided>
            <span @click="handleLogout" style="display:block;">나가기</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-menu>
</template>

<style lang="scss" scoped>
.navbar {
  height: 50px;
  line-height: 50px;
  border-radius: 0px !important;

  .hamburger-container {
    line-height: 58px;
    height: 50px;
    float: left;
    padding: 0 10px;
  }

  .screenfull {
    position: absolute;
    right: 90px;
    top: 16px;
    color: red;
  }

  .avatar-container {
    height: 50px;
    display: inline-block;
    position: absolute;
    right: 35px;

    .avatar-wrapper {
      cursor: pointer;
      margin-top: 5px;
      position: relative;

      .user-avatar {
        width: 40px;
        height: 40px;
        border-radius: 10px;
      }

      .el-icon-caret-bottom {
        position: absolute;
        right: -20px;
        top: 25px;
        font-size: 12px;
      }
    }
  }
}
</style>

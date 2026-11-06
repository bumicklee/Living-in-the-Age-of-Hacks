<script lang="ts" setup>
import { useRouter } from 'vue-router'
import img_404 from '@/assets/images/gif_404.gif'

// 컴포넌트 이름 정의
defineOptions({
  name: 'wrongPage'
})

// 라우트 사용
const router = useRouter()

// 메서드 정의
const handleGoMain = () => {
  router.push({ path: '/' })
}
</script>
<template>
  <div>
    <div class="app-container">
      <el-row>
        <el-col :span="12">
          <img :src="img_404" alt="404" class="img-style">
        </el-col>
        <el-col :span="12">
          <div style="margin-left: 100px;margin-top: 60px">
            <h1 class="color-main">OOPS!</h1>
            <h2 style="color: #606266">죄송합니다. 페이지가 길을 잃었어요!</h2>
            <div style="color:#909399;font-size: 14px">입력하신 주소가 올바른지 확인해 주세요. 아래 버튼을 눌러 홈으로 돌아가거나 오류 보고서를 보내 주세요</div>
            <el-button style="margin-top: 20px" type="primary" round @click="handleGoMain">홈으로 돌아가기</el-button>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.app-container {
  width: 80%;
  margin: 120px auto;
}

.img-style {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 100%;
}
</style>

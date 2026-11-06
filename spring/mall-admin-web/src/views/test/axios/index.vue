<script lang="ts" setup>
import { getAdminInfoAPI, adminLoginAPI } from '@/apis/admin';
import { ref } from 'vue'

defineOptions({
  name: 'TestAxios'
})

const loginData = ref()
const infoData = ref()

const onLogin = async () => {
  const res = await adminLoginAPI({
    username: 'test',
    password: '123456'
  })
  loginData.value = res.data
}

const getInfo = async () => {
  const res = await getAdminInfoAPI()
  infoData.value = res.data
}

</script>

<template>
  <button @click="onLogin">로그인</button>
  <div>
    API 반환 결과:{{ loginData }}
  </div>
  <button @click="getInfo">사용자 정보 조회</button>
  <div>
    API 반환 결과:{{ infoData }}
  </div>
</template>

<style scoped></style>

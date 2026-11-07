<script lang="ts" setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user';
import { isvalidUsername } from '@/utils/validate';
import login_center_bg from '@/assets/images/login_center_bg.png'
import { type FormInstance, type FormRules } from 'element-plus';

// Vue Router와Pinia
const router = useRouter()
const userStore = useUserStore()

// 폼 참조
const loginFormRef = ref<FormInstance>()

// 로그인 폼 데이터
const loginForm = reactive({
  username: '',
  password: '',
})

// 폼 파라미터 검증 규칙
const loginRules = reactive<FormRules<typeof loginForm>>({
  username: [{ required: true, trigger: 'blur', validator: validateUsername }],
  password: [{ required: true, trigger: 'blur', validator: validatePass }]
})

// 로그인 버튼 진행 표시줄
const loading = ref(false)
// 공식 계정 대화상자 표시 여부
const dialogVisible = ref(false)

// 사용자 이름 검증 함수
function validateUsername(rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!isvalidUsername(value)) {
    callback(new Error('올바른 사용자 이름을 입력하세요'))
  } else {
    callback()
  }
};

// 비밀번호 검증 함수
function validatePass(rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value.length < 3) {
    callback(new Error('비밀번호는 3자리 이상이어야 합니다'))
  } else {
    callback()
  }
};

// 컴포넌트 마운트 완료 후 호출
onMounted(() => {
  loginForm.username = userStore.userInfo.username
  loginForm.password = userStore.userInfo.password
  if (loginForm.username === undefined || loginForm.username == null || loginForm.username === '') {
    loginForm.username = 'admin';
  }
})

// 로그인 버튼 이벤트 처리
const handleLogin = () => {
  loginFormRef.value!.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.userLogin({
          username: loginForm.username.trim(),
          password: loginForm.password
        })
        loading.value = false
        router.push({ path: '/' })
      }
      catch (err) {
        loading.value = false
        console.log(err)
      }
    } else {
      console.log('파라미터 검증이 올바르지 않습니다!')
    }
  })
}
</script>

<template>
  <div>
    <el-card class="login-form-layout">
      <el-form autoComplete="on" :model="loginForm" :rules="loginRules" ref="loginFormRef" label-position="left">
        <div style="text-align: center">
          <svg-icon icon-class="login-mall" style="width: 56px;height: 56px;color: #409EFF"></svg-icon>
        </div>
        <h2 class="login-title color-main">mall-admin-web</h2>
        <el-form-item prop="username">
          <el-input data-testid="login-username" name="username" type="text" v-model="loginForm.username" autoComplete="on" placeholder="사용자 이름을 입력하세요">
            <template #prefix>
              <span>
                <svg-icon icon-class="user" class="color-main"></svg-icon>
              </span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input data-testid="login-password" name="password" @keyup.enter="handleLogin" v-model="loginForm.password" autoComplete="on"
            show-password placeholder="비밀번호를 입력하세요">
            <template #prefix>
              <span>
                <svg-icon icon-class="password" class="color-main"></svg-icon>
              </span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item style="margin-bottom: 60px;text-align: center">
          <el-button data-testid="login-submit" style="width: 45%" type="primary" :loading="loading" @click="handleLogin">
            로그인
          </el-button>
          <el-button style="width: 45%" type="primary" @click="dialogVisible = true">
            체험 계정 조회
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <img :src="login_center_bg" class="login-center-layout">
    <el-dialog title="공식 계정 QR 코드" v-model="dialogVisible" :show-close="false" :center="true" width="30%">
      <div style="text-align: center">
        <span class="font-title-large"><span class="color-main font-extra-large">공식 계정 팔로우</span>답변<span
            class="color-main font-extra-large">체험</span>체험 계정 조회</span>
        <br>
        <img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/banner/qrcode_for_macrozheng_258.jpg" width="160"
          height="160" style="margin-top: 10px">
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="dialogVisible = false">확인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-form-layout {
  position: absolute;
  left: 0;
  right: 0;
  width: 360px;
  margin: 140px auto;
  border-top: 10px solid #409EFF;
}

.login-title {
  text-align: center;
}

.login-center-layout {
  background: #409EFF;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 100%;
  margin-top: 200px;
}
</style>

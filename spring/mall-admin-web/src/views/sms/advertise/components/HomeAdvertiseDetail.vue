<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import SingleUpload from '@/components/Upload/singleUpload.vue'
import { homeAdvertiseCreateAPI, getHomeAdvertiseByIdAPI, homeAdvertiseUpdateAPI } from '@/apis/homeAdvertise'
import type { SmsHomeAdvertise } from '@/types/homeAdvertist'

// 라우트 객체 조회
const router = useRouter()
const route = useRoute()

// 속성 정의
const props = defineProps({
  // 편집 모드 여부
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 기본 배너 광고, 추가 시 사용
const defaultHomeAdvertise: SmsHomeAdvertise = {
  name: '',
  type: 1,
  status: 0,
  sort: 0
}
// 롤링 배너 광고 객체 정의
const homeAdvertise = ref<SmsHomeAdvertise>(Object.assign({}, defaultHomeAdvertise))
// 배너 광고 위치
const typeOptions = ref([
  {
    label: 'PC홈 배너',
    value: 0
  },
  {
    label: 'APP홈 배너',
    value: 1
  }
])
// 배너 광고 폼 컴포넌트 참조
const homeAdvertiseFrom = ref<FormInstance>()
// 배너 광고 폼 검증 규칙
const rules = reactive<FormRules<SmsHomeAdvertise>>({
  name: [
    { required: true, message: '광고 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '광고 링크를 입력하세요', trigger: 'blur' }
  ],
  startTime: [
    { required: true, message: '시작 시간을 선택하세요', trigger: 'blur' }
  ],
  endTime: [
    { required: true, message: '만료 시간을 선택하세요', trigger: 'blur' }
  ],
  pic: [
    { required: true, message: '광고 이미지를 선택하세요', trigger: 'blur' }
  ]
})

// 페이지 로딩 시 데이터 조회
onMounted(async () => {
  if (props.isEdit) {
    const res = await getHomeAdvertiseByIdAPI(Number(route.query.id))
    homeAdvertise.value = res.data
  } else {
    homeAdvertise.value = Object.assign({}, defaultHomeAdvertise)
  }
})

// 폼 제출
const onSubmit = async () => {
  homeAdvertiseFrom.value!.validate(async (valid) => {
    if (valid) {
      await ElMessageBox.confirm('데이터 제출 여부', '안내', {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'warning'
      })
      if (props.isEdit) {
        await homeAdvertiseUpdateAPI(Number(route.query.id), homeAdvertise.value)
        homeAdvertiseFrom.value!.resetFields()
        ElMessage({
          message: '수정 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      } else {
        await homeAdvertiseCreateAPI(homeAdvertise.value)
        homeAdvertiseFrom.value!.resetFields()
        homeAdvertise.value = Object.assign({}, defaultHomeAdvertise)
        ElMessage({
          message: '제출 성공',
          type: 'success',
          duration: 1000
        })
      }
    } else {
      ElMessage({
        message: '검증 실패',
        type: 'error',
        duration: 1000
      })
    }
  })
}

// 폼 초기화
const resetForm = () => {
  if (homeAdvertiseFrom.value) {
    homeAdvertiseFrom.value.resetFields()
    homeAdvertise.value = Object.assign({}, defaultHomeAdvertise)
  }
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form ref="homeAdvertiseFrom" :model="homeAdvertise" :rules="rules" label-width="150px">
      <el-form-item label="광고명:" prop="name">
        <el-input v-model="homeAdvertise.name" class="input-width"></el-input>
      </el-form-item>
      <el-form-item label="광고 위치:">
        <el-select v-model="homeAdvertise.type">
          <el-option v-for="type in typeOptions" :key="type.value" :label="type.label" :value="type.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="시작 시간:" prop="startTime">
        <el-date-picker type="datetime" placeholder="날짜 선택" v-model="homeAdvertise.startTime"></el-date-picker>
      </el-form-item>
      <el-form-item label="만료 시간:" prop="endTime">
        <el-date-picker type="datetime" placeholder="날짜 선택" v-model="homeAdvertise.endTime"></el-date-picker>
      </el-form-item>
      <el-form-item label="온라인/오프라인:">
        <el-radio-group v-model="homeAdvertise.status">
          <el-radio :label="0">오프라인</el-radio>
          <el-radio :label="1">온라인</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="광고 이미지:">
        <single-upload v-model="homeAdvertise.pic"></single-upload>
      </el-form-item>
      <el-form-item label="정렬:">
        <el-input v-model="homeAdvertise.sort" class="input-width"></el-input>
      </el-form-item>
      <el-form-item label="광고 링크:" prop="url">
        <el-input v-model="homeAdvertise.url" class="input-width"></el-input>
      </el-form-item>
      <el-form-item label="광고 비고:">
        <el-input class="input-width" type="textarea" :rows="5" placeholder="내용을 입력하세요" v-model="homeAdvertise.note">
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit()">제출</el-button>
        <el-button v-if="!props.isEdit" @click="resetForm()">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.input-width {
  width: 70%;
}
</style>

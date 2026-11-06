<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { createBrandAPI, getBrandAPI, updateBrandAPI } from '@/apis/brand'
import SingleUpload from '@/components/Upload/singleUpload.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { PmsBrand } from '@/types/brand'

// 라우트 객체 조회
const route = useRoute()
const router = useRouter()

// 속성 정의
const props = defineProps({
  // 편집 모드 여부
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 브랜드 정의id
const brandId = computed(() => Number(route.query.id))

// 기본 브랜드, 추가 시 사용
const defaultBrand: PmsBrand = {
  bigPic: '',
  brandStory: '',
  factoryStatus: 0,
  firstLetter: '',
  logo: '',
  name: '',
  showStatus: 0,
  sort: 0
}
// 브랜드 객체 정의
const brand = ref<PmsBrand>(Object.assign({}, defaultBrand))
// 브랜드 폼 컴포넌트 참조
const brandFromRef = ref<FormInstance>()
// 브랜드 폼 검증 규칙
const rules = reactive<FormRules<typeof brand>>({
  name: [
    { required: true, message: '브랜드 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  logo: [
    { required: true, message: '브랜드를 입력하세요logo', trigger: 'blur' }
  ],
  sort: [
    { type: 'number', message: '정렬 값은 숫자여야 합니다' }
  ],
})

// 컴포넌트 마운트 시 데이터 로드
onMounted(async () => {
  if (props.isEdit) {
    // 편집 모드에서 API를 호출해 브랜드 상세 조회
    const res = await getBrandAPI(brandId.value)
    brand.value = res.data
  } else {
    // 추가 모드에서는 기본 브랜드 사용
    brand.value = Object.assign({}, defaultBrand)
  }
})

// 브랜드 폼 제출 처리
const handleBrandSubmit = () => {
  brandFromRef.value!.validate(async (valid) => {
    if (valid) {
      await ElMessageBox.confirm('데이터 제출 여부', '안내', {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'warning'
      })
      if (props.isEdit) {
        await updateBrandAPI(brandId.value, brand.value)
        brandFromRef.value!.resetFields()
        ElMessage({
          message: '수정 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      } else {
        await createBrandAPI(brand.value)
        brandFromRef.value!.resetFields()
        brand.value = Object.assign({}, defaultBrand)
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

// 브랜드 폼 초기화 처리
const handleBrandReset = () => {
  brandFromRef.value!.resetFields()
  brand.value = Object.assign({}, defaultBrand)
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form :model="brand" :rules="rules" ref="brandFromRef" label-width="150px">
      <el-form-item label="브랜드명:" prop="name">
        <el-input v-model="brand.name"></el-input>
      </el-form-item>
      <el-form-item label="브랜드 첫 글자:">
        <el-input v-model="brand.firstLetter"></el-input>
      </el-form-item>
      <el-form-item label="브랜드 LOGO:" prop="logo">
        <single-upload v-model="brand.logo"></single-upload>
      </el-form-item>
      <el-form-item label="브랜드 전문관 대형 이미지:">
        <single-upload v-model="brand.bigPic"></single-upload>
      </el-form-item>
      <el-form-item label="브랜드 스토리:">
        <el-input placeholder="내용을 입력하세요" type="textarea" v-model="brand.brandStory" :autosize="true"></el-input>
      </el-form-item>
      <el-form-item label="정렬:" prop="sort">
        <el-input v-model.number="brand.sort"></el-input>
      </el-form-item>
      <el-form-item label="표시 여부:">
        <el-radio-group v-model="brand.showStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="브랜드 제조사:">
        <el-radio-group v-model="brand.factoryStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleBrandSubmit()">제출</el-button>
        <el-button v-if="!props.isEdit" @click="handleBrandReset()">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style></style>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getProductAttributeCategoryListAPI } from '@/apis/productAttrCate'
import { productAttributeCreateAPI, getProductAttributeByIdAPI, productAttributeUpdateAPI } from '@/apis/productAttr'
import type { PmsProductAttribute, PmsProductAttributeCategory } from '@/types/productAttr'

// 라우트 객체 조회
const route = useRoute()
const router = useRouter()

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 기본 상품 속성
const defaultProductAttr: PmsProductAttribute = {
  filterType: 0,
  handAddStatus: 0,
  inputList: '',
  inputType: 0,
  name: '',
  productAttributeCategoryId: 0,
  relatedStatus: 0,
  searchType: 0,
  selectType: 0,
  sort: 0,
  type: 0
}
// 현재 작업 중인 상품 속성
const productAttr = ref(Object.assign({}, defaultProductAttr))
// 상품 속성 카테고리 목록
const productAttrCateList = ref<PmsProductAttributeCategory[]>([])
// 속성값 입력 방식 포맷
const inputListFormat = computed({
  get() {
    // 쉼표로 구분된 문자열을 줄바꿈 구분으로 변환(표시용)
    return productAttr.value.inputList ? productAttr.value.inputList.replace(/,/g, '\n') : ''
  },
  set(value) {
    // 줄바꿈으로 구분된 문자열을 쉼표 구분으로 변환(저장용)
    productAttr.value.inputList = value ? value.replace(/\n/g, ',') : ''
  }
})
// 카테고리 목록 조회
const getCateList = async () => {
  const listQuery = { pageNum: 1, pageSize: 100 }
  const res = await getProductAttributeCategoryListAPI(listQuery)
  productAttrCateList.value = res.data.list
}

// 컴포넌트 마운트 시 데이터 로드
onMounted(async () => {
  if (props.isEdit) {
    const res = await getProductAttributeByIdAPI(Number(route.query.id))
    productAttr.value = res.data
  } else {
    resetProductAttr()
  }
  getCateList()
})

// 상품 속성 폼 컴포넌트 참조
const productAttrFrom = ref<FormInstance>()
// 상품 속성 폼 검증 규칙
const rules = reactive<FormRules<typeof productAttr>>({
  name: [
    { required: true, message: '속성 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ]
})

// 상품 속성 초기화
const resetProductAttr = () => {
  productAttr.value = Object.assign({}, defaultProductAttr)
  productAttr.value.productAttributeCategoryId = Number(route.query.cid)
  productAttr.value.type = Number(route.query.type)
}

// 폼 제출 처리
const onSubmit = async () => {
  if (!productAttrFrom.value) return
  const valid = await productAttrFrom.value.validate().catch(() => false)
  if (valid) {
    await ElMessageBox.confirm('데이터 제출 여부', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (props.isEdit) {
      await productAttributeUpdateAPI(Number(route.query.id), productAttr.value)
      ElMessage.success('수정 성공')
      router.back()
    } else {
      await productAttributeCreateAPI(productAttr.value)
      ElMessage.success('제출 성공')
      resetForm()
    }
  } else {
    ElMessage.error('검증 실패')
  }
}

// 폼 초기화
const resetForm = () => {
  if (productAttrFrom.value) {
    productAttrFrom.value.resetFields()
    resetProductAttr()
  }
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form :model="productAttr" :rules="rules" ref="productAttrFrom" label-width="150px">
      <el-form-item label="속성명:" prop="name">
        <el-input v-model="productAttr.name"></el-input>
      </el-form-item>
      <el-form-item label="상품 유형:">
        <el-select v-model="productAttr.productAttributeCategoryId" placeholder="선택하세요">
          <el-option v-for="item in productAttrCateList" :key="item.id" :label="item.name" :value="item.id!">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="카테고리 필터 스타일:">
        <el-radio-group v-model="productAttr.filterType">
          <el-radio :label="0">일반</el-radio>
          <el-radio :label="1">색상</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="검색 가능 여부:">
        <el-radio-group v-model="productAttr.searchType">
          <el-radio :label="0">검색 불필요</el-radio>
          <el-radio :label="1">키워드 검색</el-radio>
          <el-radio :label="2">범위 검색</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="상품 속성 연관:">
        <el-radio-group v-model="productAttr.relatedStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="속성 선택 가능 여부:">
        <el-radio-group v-model="productAttr.selectType">
          <el-radio :label="0">고유</el-radio>
          <el-radio :label="1">단일 선택</el-radio>
          <el-radio :label="2">다중 선택</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="속성값 입력 방식:">
        <el-radio-group v-model="productAttr.inputType">
          <el-radio :label="0">직접 입력</el-radio>
          <el-radio :label="1">아래 목록에서 선택</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="속성값 선택값 목록:">
        <el-input :autosize="true" type="textarea" v-model="inputListFormat"></el-input>
      </el-form-item>
      <el-form-item label="직접 추가 지원 여부:">
        <el-radio-group v-model="productAttr.handAddStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="정렬 속성:">
        <el-input v-model="productAttr.sort"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit">제출</el-button>
        <el-button v-if="!isEdit" @click="resetForm">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped></style>

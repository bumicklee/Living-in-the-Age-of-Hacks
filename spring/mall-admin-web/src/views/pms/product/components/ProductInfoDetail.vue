<script setup lang="ts">
import { ref, onMounted, watch, inject, type Ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getProductCategoryListWithChildrenAPI } from '@/apis/productCate'
import { getBrandListAPI } from '@/apis/brand'
import type { PmsProductParam } from '@/types/product'
import type { ElCascaderDataVo, ElSelectDataVo } from '@/types/common'

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 이벤트 정의
const emit = defineEmits(['next-step'])

// 계층 간 전달 데이터 조회
const compProductParam = inject('product-key') as Ref<PmsProductParam>

// 캐스케이더에서 현재 선택된 상품 카테고리, 구조: [상위 카테고리 ID, 카테고리ID]
const selectProductCateValue = ref<number[]>([])
// 캐스케이더의 상품 카테고리 데이터
const productCateOptions = ref<ElCascaderDataVo[]>([])
// 상품 카테고리 목록 데이터 조회
const getProductCateList = async () => {
  const res = await getProductCategoryListWithChildrenAPI()
  const list = res.data
  productCateOptions.value = list.map(item => ({
    label: item.name,
    value: item.id!,
    children: item.children?.map(it => ({ label: it.name, value: it.id! }))
  }))
}
// 선택기의 브랜드 데이터
const brandOptions = ref<ElSelectDataVo[]>([])
// 브랜드 목록 데이터 조회
const getBrandList = async () => {
  const res = await getBrandListAPI({ pageNum: 1, pageSize: 100 })
  brandOptions.value = res.data.list.map(item => ({ label: item.name, value: item.id! }))
}

// 데이터 초기화
onMounted(async () => {
  await getProductCateList()
  await getBrandList()
  if (props.isEdit) {
    handleEditCreated()
  }
})

// 편집 상태 초기화 플래그
const hasEditCreated = ref(false)

// 폼 참조
const productInfoForm = ref<FormInstance>()
// 폼 검증 규칙
const rules = {
  name: [
    { required: true, message: '상품 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  subTitle: [{ required: true, message: '상품 부제목을 입력하세요', trigger: 'blur' }],
  productCategoryId: [{ required: true, message: '상품 카테고리를 선택하세요', trigger: 'blur' }],
  brandId: [{ required: true, message: '상품 브랜드를 선택하세요', trigger: 'blur' }],
  description: [{ required: true, message: '상품 소개를 입력하세요', trigger: 'blur' }],
  requiredProp: [{ required: true, message: '필수 입력 항목입니다', trigger: 'blur' }]
}

// 카테고리 선택 변경 감시
watch(selectProductCateValue, (newValue) => {
  if (newValue && newValue.length === 2) {
    compProductParam.value.productCategoryId = newValue[1]
    compProductParam.value.productCategoryName = getCateNameById(compProductParam.value.productCategoryId!)
  } else {
    compProductParam.value.productCategoryId = undefined
    compProductParam.value.productCategoryName = undefined
  }
})


// 메서드 정의
const handleEditCreated = () => {
  if (compProductParam.value.productCategoryId) {
    selectProductCateValue.value.push(compProductParam.value.cateParentId!)
    selectProductCateValue.value.push(compProductParam.value.productCategoryId)
  }
  hasEditCreated.value = true
}

// 카테고리 ID로 카테고리 이름 조회
const getCateNameById = (id: number) => {
  for (const item of productCateOptions.value) {
    const child = item.children?.find(child => child.value === id)
    if (child) {
      return child.label
    }
  }
  return undefined
}

// 다음 단계 버튼 이벤트
const handleNext = async () => {
  if (productInfoForm.value) {
    try {
      const valid = await productInfoForm.value.validate()
      if (valid) {
        emit('next-step')
      }
    } catch {
      ElMessage({
        message: '검증 실패',
        type: 'error',
        duration: 1000
      })
    }
  }
}

// 브랜드 변경 처리
const handleBrandChange = (val: number) => {
  const findBrand = brandOptions.value.find(item => item.value === val)
  compProductParam.value.brandName = findBrand?.label
}
</script>

<template>
  <div style="margin-top: 50px">
    <el-form :model="compProductParam" :rules="rules" ref="productInfoForm" label-width="120px"
      class="form-inner-container">
      <el-form-item label="상품 카테고리:" prop="productCategoryId">
        <el-cascader v-model="selectProductCateValue" :options="productCateOptions">
        </el-cascader>
      </el-form-item>
      <el-form-item label="상품명:" prop="name">
        <el-input v-model="compProductParam.name"></el-input>
      </el-form-item>
      <el-form-item label="부제목:" prop="subTitle">
        <el-input v-model="compProductParam.subTitle"></el-input>
      </el-form-item>
      <el-form-item label="상품 브랜드:" prop="brandId">
        <el-select v-model="compProductParam.brandId" @change="handleBrandChange" placeholder="브랜드를 선택하세요">
          <el-option v-for="item in brandOptions" :key="item.value" :label="item.label" :value="item.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="상품 소개:">
        <el-input :autoSize="true" v-model="compProductParam.description" type="textarea"
          placeholder="내용을 입력하세요"></el-input>
      </el-form-item>
      <el-form-item label="상품 코드:">
        <el-input v-model="compProductParam.productSn"></el-input>
      </el-form-item>
      <el-form-item label="상품 판매가:">
        <el-input v-model="compProductParam.price"></el-input>
      </el-form-item>
      <el-form-item label="시장가:">
        <el-input v-model="compProductParam.originalPrice"></el-input>
      </el-form-item>
      <el-form-item label="상품 재고:">
        <el-input v-model="compProductParam.stock"></el-input>
      </el-form-item>
      <el-form-item label="단위:">
        <el-input v-model="compProductParam.unit"></el-input>
      </el-form-item>
      <el-form-item label="상품 중량:">
        <el-input v-model="compProductParam.weight" style="width: 300px"></el-input>
        <span style="margin-left: 20px">g</span>
      </el-form-item>
      <el-form-item label="정렬">
        <el-input v-model="compProductParam.sort"></el-input>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%;text-align: center;">
          <el-button type="primary" @click="handleNext()">다음 단계, 상품 프로모션 입력</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped></style>

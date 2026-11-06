<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getProductCategoryListAPI, productCategoryCreateAPI, productCategoryUpdateByIdAPI, getProductCategoryByIdAPI } from '@/apis/productCate'
import { productAttributeCategoryListWithAttrAPI } from '@/apis/productAttrCate'
import { getProductAttrInfoByCateIdAPI } from '@/apis/productAttr'
import SingleUpload from '@/components/Upload/singleUpload.vue'
import type { PmsProductCategory } from '@/types/productCate'
import type { ElCascaderDataVo } from '@/types/common'

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

// 기본 상품 카테고리
const defaultProductCate: PmsProductCategory = {
  description: '',
  icon: '',
  keywords: '',
  name: '',
  navStatus: 0,
  parentId: 0,
  productUnit: '',
  showStatus: 0,
  sort: 0
}
// 카테고리 id 정의(편집 시 사용)
const cateId = computed(() => Number(route.query.id))
// 현재 작업 중인 상품 카테고리
const productCate = ref<PmsProductCategory>(Object.assign({}, defaultProductCate))
// 선택 가능한 상위 카테고리
const selectProductCateList = ref<PmsProductCategory[]>([])
// 상품 속성 ID 및 속성 카테고리 ID 집합(필터 속성)
const filterProductAttrList = ref<{ key: number, value: number[] }[]>([{ key: 0, value: [] }])
// 필터 속성 캐스케이더의 데이터
const filterAttrs = ref<ElCascaderDataVo[]>([])
// 선택 가능한 상위 카테고리 목록 조회
const getSelectProductCateList = async () => {
  const res = await getProductCategoryListAPI(0, { pageSize: 100, pageNum: 1 })
  selectProductCateList.value = res.data.list
  const noTopProductCate = Object.assign({}, defaultProductCate)
  noTopProductCate.id = 0
  noTopProductCate.name = '상위 카테고리 없음'
  selectProductCateList.value.unshift(noTopProductCate)
}
// 상품 속성 카테고리 목록 조회
const getProductAttrCateList = async () => {
  const res = await productAttributeCategoryListWithAttrAPI()
  const list = res.data
  filterAttrs.value = list.map(item => ({
    label: item.name,
    value: item.id!,
    children: item.productAttributeList?.map(it => ({ label: it.name, value: it.id! }))
  }))
}

// 컴포넌트 마운트 후 실행
onMounted(async () => {
  if (props.isEdit) {
    const res = await getProductCategoryByIdAPI(cateId.value)
    productCate.value = res.data
    const attrRes = await getProductAttrInfoByCateIdAPI(cateId.value)
    filterProductAttrList.value = attrRes.data.map((item, index) => ({
      key: Date.now() + index,
      value: [item.attributeCategoryId, item.attributeId]
    }))
  } else {
    productCate.value = Object.assign({}, defaultProductCate)
  }
  getSelectProductCateList()
  getProductAttrCateList()
})

// 상품 카테고리 폼 참조
const productCateFrom = ref<FormInstance>()
// 상품 카테고리 폼 검증 규칙
const rules = reactive<FormRules<PmsProductCategory>>({
  name: [
    { required: true, message: '카테고리 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ]
})

// 선택된 필터 상품 속성 조회ID
const getProductAttributeIdList = () => {
  return filterProductAttrList.value.filter(item => item.value && item.value.length === 2)
    .map(item => item.value[1]!)
}

// 폼 제출
const onSubmit = async () => {
  if (!productCateFrom.value) return
  const valid = await productCateFrom.value.validate().catch(() => false)
  if (valid) {
    await ElMessageBox.confirm('데이터 제출 여부', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (props.isEdit) {
      productCate.value.productAttributeIdList = getProductAttributeIdList()
      await productCategoryUpdateByIdAPI(cateId.value, productCate.value)
      ElMessage({
        message: '수정 성공',
        type: 'success',
        duration: 1000
      })
      router.back()
    } else {
      productCate.value.productAttributeIdList = getProductAttributeIdList()
      await productCategoryCreateAPI(productCate.value)
      productCateFrom.value.resetFields()
      resetForm()
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
}

// 폼 초기화
const resetForm = () => {
  if (productCateFrom.value) {
    productCateFrom.value.resetFields()
    productCate.value = Object.assign({}, defaultProductCate)
    getSelectProductCateList()
    filterProductAttrList.value = [{ key: 0, value: [] }]
  }
}

// 필터 속성 삭제
const removeFilterAttr = (filterProductAttr: { key: number, value: number[] }) => {
  if (filterProductAttrList.value.length === 1) {
    ElMessage({
      message: '최소 하나는 남겨야 함',
      type: 'warning',
      duration: 1000
    })
    return
  }
  const index = filterProductAttrList.value.indexOf(filterProductAttr)
  if (index !== -1) {
    filterProductAttrList.value.splice(index, 1)
  }
}

// 필터 속성 추가
const handleAddFilterAttr = () => {
  if (filterProductAttrList.value.length === 3) {
    ElMessage({
      message: '최대 세 개까지 추가',
      type: 'warning',
      duration: 1000
    })
    return
  }
  filterProductAttrList.value.push({
    value: [],
    key: Date.now()
  })
}

// 필터
const filterLabelFilter = (index: number) => {
  if (index === 0) {
    return '필터 속성:'
  } else {
    return ''
  }
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form ref="productCateFrom" :model="productCate" :rules="rules" label-width="150px">
      <el-form-item label="카테고리 이름:" prop="name">
        <el-input v-model="productCate.name"></el-input>
      </el-form-item>
      <el-form-item label="상위 카테고리:">
        <el-select v-model="productCate.parentId" placeholder="카테고리를 선택하세요">
          <el-option v-for="item in selectProductCateList" :key="item.id" :label="item.name" :value="item.id!">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="수량 단위:">
        <el-input v-model="productCate.productUnit"></el-input>
      </el-form-item>
      <el-form-item label="정렬:">
        <el-input v-model="productCate.sort"></el-input>
      </el-form-item>
      <el-form-item label="표시 여부:">
        <el-radio-group v-model="productCate.showStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="내비게이션 바에 표시 여부:">
        <el-radio-group v-model="productCate.navStatus">
          <el-radio :label="1">예</el-radio>
          <el-radio :label="0">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="카테고리 아이콘:">
        <single-upload v-model="productCate.icon"></single-upload>
      </el-form-item>
      <el-form-item v-for="(filterProductAttr, index) in filterProductAttrList" :label="filterLabelFilter(index)"
        :key="filterProductAttr.key">
        <el-cascader clearable v-model="filterProductAttr.value" :options="filterAttrs">
        </el-cascader>
        <el-button style="margin-left: 20px" @click.prevent="removeFilterAttr(filterProductAttr)">삭제</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleAddFilterAttr()">추가</el-button>
      </el-form-item>
      <el-form-item label="키워드:">
        <el-input v-model="productCate.keywords"></el-input>
      </el-form-item>
      <el-form-item label="카테고리 설명:">
        <el-input type="textarea" :autosize="true" v-model="productCate.description"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit()">제출</el-button>
        <el-button v-if="!props.isEdit" @click="resetForm()">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped></style>

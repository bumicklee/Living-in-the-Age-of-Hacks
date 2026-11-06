<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus'
import { couponCreateAPI, getCouponByIdAPI, couponUpdateByIdAPI } from '@/apis/coupon'
import { getProductListAPI } from '@/apis/product'
import { getProductCategoryListWithChildrenAPI } from '@/apis/productCate'
import { useRoute, useRouter } from 'vue-router'
import type { CouponSelectProductOptionVo, SmsCouponExt } from '@/types/coupon'
import { couponPlatforms, couponTypes } from '@/utils/constant'
import type { ElCascaderDataVo } from '@/types/common'

// 라우트 조회
const route = useRoute()
const router = useRouter()

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 기본 쿠폰, 추가 시 사용
const defaultCoupon: SmsCouponExt = {
  type: 0,
  name: '',
  platform: 0,
  amount: 0,
  perLimit: 1,
  useType: 0,
  productRelationList: [],
  productCategoryRelationList: []
}
// 현재 작업 중인 쿠폰 객체
const coupon = ref(Object.assign({}, defaultCoupon))
// 사용 유형이 지정 카테고리일 때의 상품 카테고리 정보 선택
const productCateOptions = ref<ElCascaderDataVo[]>([])
// 쿠폰 유형이 지정 카테고리일 때 선택한 상품 카테고리 ID([상위,하위])
const selectProductCate = ref<number[]>([])
// 카테고리 목록 조회
const getProductCateList = async () => {
  try {
    const res = await getProductCategoryListWithChildrenAPI()
    const list = res.data
    productCateOptions.value = list.map(item => ({
      label: item.name,
      value: item.id!,
      children: item.children?.map(it => ({ label: it.name, value: it.id! }))
    }))
  } catch (error) {
    console.error('상품 카테고리 목록 조회 실패:', error)
  }
}

// 데이터 초기화
onMounted(async () => {
  if (props.isEdit) {
    const response = await getCouponByIdAPI(Number(route.query.id))
    coupon.value = response.data
  }
  await getProductCateList()
})

// 쿠폰 유형이 지정 상품일 때 테이블 데이터 로딩 상태
const selectProductLoading = ref(false)
// 쿠폰 유형이 지정 상품일 때 테이블에 제공되는 상품 목록 옵션
const selectProductOptions = ref<CouponSelectProductOptionVo[]>([])
// 쿠폰 유형이 지정 상품일 때 현재 선택된 연관 상품ID
const selectProduct = ref<number>()


// 폼 참조
const couponFrom = ref()
// 폼 검증 규칙
const rules = reactive<FormRules>({
  name: [
    { required: true, message: '쿠폰 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  publishCount: [
    { type: 'number', required: true, message: '양의 정수만 입력할 수 있습니다', trigger: 'blur' }
  ],
  amount: [
    { type: 'number', required: true, message: '액면가는 숫자만 가능, 0.01~10000, 소수점 2자리까지', trigger: 'blur' }
  ],
  minPoint: [
    { type: 'number', required: true, message: '양의 정수만 입력할 수 있습니다', trigger: 'blur' }
  ]
})

// 폼 제출
const onSubmit = async () => {
  if (!couponFrom.value) return
  const isValid = await couponFrom.value.validate().catch(() => false)
  if (isValid) {
    await ElMessageBox.confirm('데이터 제출 여부', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (props.isEdit) {
      try {
        await couponUpdateByIdAPI(Number(route.query.id), coupon.value)
        couponFrom.value.resetFields()
        ElMessage({
          message: '수정 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      } catch (error) {
        console.error('쿠폰 업데이트 실패:', error)
      }
    } else {
      try {
        await couponCreateAPI(coupon.value)
        couponFrom.value.resetFields()
        ElMessage({
          message: '제출 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      } catch (error) {
        console.error('쿠폰 생성 실패:', error)
      }
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
  if (couponFrom.value) {
    couponFrom.value.resetFields()
  }
  coupon.value = Object.assign({}, defaultCoupon)
}

// 상품 검색 메서드
const searchProductMethod = async (query: string) => {
  if (query) {
    selectProductLoading.value = true
    try {
      const res = await getProductListAPI({ pageNum: 1, pageSize: 10, keyword: query })
      selectProductLoading.value = false
      const productList = res.data.list
      selectProductOptions.value = productList.map(item => ({
        productId: item.id,
        productName: item.name,
        productSn: item.productSn
      }))
    } catch (error) {
      selectProductLoading.value = false
      console.error('상품 검색 실패:', error)
    }
  } else {
    selectProductOptions.value = []
  }
}

// 상품 연관 추가
const handleAddProductRelation = () => {
  if (!selectProduct.value) {
    ElMessage({
      message: '먼저 상품을 선택해 주세요',
      type: 'warning'
    })
    return
  }
  coupon.value.productRelationList!.push(getProductById(selectProduct.value)!)
  selectProduct.value = undefined
}

// 상품 연관 삭제
const handleDeleteProductRelation = (index: number) => {
  coupon.value.productRelationList!.splice(index, 1)
}

// 상품 카테고리 연관 추가
const handleAddProductCategoryRelation = () => {
  if (selectProductCate.value.length <= 0) {
    ElMessage({
      message: '먼저 상품 카테고리를 선택해 주세요',
      type: 'warning'
    })
    return
  }
  coupon.value.productCategoryRelationList!.push(getProductCateByIds(selectProductCate.value))
  selectProductCate.value = []
}

// 상품 카테고리 연관 삭제
const handleDeleteProductCateRelation = (index: number) => {
  coupon.value.productCategoryRelationList!.splice(index, 1)
}

// ID로 연관 상품 조회
const getProductById = (id: number) => {
  return selectProductOptions.value.find(item => item.productId === id)
}

// IDs로 상품 카테고리 조회
const getProductCateByIds = (ids: number[]) => {
  const findParentCate = productCateOptions.value.find(item => item.value === ids[0])
  const findCate = findParentCate?.children?.find(item => item.value === ids[1])
  return { productCategoryId: ids[1], productCategoryName: findCate?.label, parentCategoryName: findParentCate?.label }
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form :model="coupon" :rules="rules" ref="couponFrom" label-width="150px">
      <el-form-item label="쿠폰 유형:">
        <el-select v-model="coupon.type">
          <el-option v-for="type in couponTypes" :key="type.value" :label="type.label" :value="type.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="쿠폰 이름:" prop="name">
        <el-input v-model="coupon.name" class="input-width"></el-input>
      </el-form-item>
      <el-form-item label="적용 플랫폼:">
        <el-select v-model="coupon.platform">
          <el-option v-for="item in couponPlatforms" :key="item.value" :label="item.label" :value="item.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="총 발행량:" prop="publishCount">
        <el-input v-model.number="coupon.publishCount" placeholder="양의 정수만 입력할 수 있습니다" class="input-width"></el-input>
      </el-form-item>
      <el-form-item label="액면가:" prop="amount">
        <el-input v-model.number="coupon.amount" placeholder="액면가는 숫자만 가능, 소수점 2자리까지" class="input-width">
          <template #append>원</template>
        </el-input>
      </el-form-item>
      <el-form-item label="1인당 발급 제한:">
        <el-input v-model="coupon.perLimit" placeholder="양의 정수만 입력할 수 있습니다" class="input-width">
          <template #append>장</template>
        </el-input>
      </el-form-item>
      <el-form-item label="사용 조건:" prop="minPoint">
        <el-input v-model.number="coupon.minPoint" placeholder="양의 정수만 입력할 수 있습니다" class="input-width">
          <template #prepend>이상</template>
          <template #append>원 사용 가능</template>
        </el-input>
      </el-form-item>
      <el-form-item label="수령 날짜:" prop="enableTime">
        <el-date-picker type="date" placeholder="날짜 선택" v-model="coupon.enableTime" class="input-width"></el-date-picker>
      </el-form-item>
      <el-form-item label="유효 기간:">
        <el-date-picker type="date" placeholder="날짜 선택" v-model="coupon.startTime" style="width: 150px"></el-date-picker>
        <span style="margin-left: 20px;margin-right: 20px">~</span>
        <el-date-picker type="date" placeholder="날짜 선택" v-model="coupon.endTime" style="width: 150px"></el-date-picker>
      </el-form-item>
      <el-form-item label="사용 가능 상품:">
        <el-radio-group v-model="coupon.useType">
          <el-radio-button :label="0">전 상품 공용</el-radio-button>
          <el-radio-button :label="1">지정 카테고리</el-radio-button>
          <el-radio-button :label="2">지정 상품</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-show="coupon.useType === 1">
        <el-cascader clearable placeholder="카테고리 이름을 선택하세요" v-model="selectProductCate" :options="productCateOptions">
        </el-cascader>
        <el-button @click="handleAddProductCategoryRelation()">추가</el-button>
        <el-table ref="productCateRelationTable" :data="coupon.productCategoryRelationList"
          style="width: 100%;margin-top: 20px" border>
          <el-table-column label="카테고리 이름" align="center">
            <template #default="scope">{{ scope.row.parentCategoryName }}>{{ scope.row.productCategoryName }}</template>
          </el-table-column>
          <el-table-column label="작업" align="center" width="100">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="handleDeleteProductCateRelation(scope.$index)">삭제
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item v-show="coupon.useType === 2">
        <el-select v-model="selectProduct" filterable remote reserve-keyword placeholder="상품명/상품 코드"
          :remote-method="searchProductMethod" :loading="selectProductLoading" style="width: 300px;">
          <el-option v-for="item in selectProductOptions" :key="item.productId" :label="item.productName"
            :value="item.productId!">
            <template #default>
              <span style="float: left">{{ item.productName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">NO.{{ item.productSn }}</span>
            </template>
          </el-option>
        </el-select>
        <el-button @click="handleAddProductRelation()">추가</el-button>
        <el-table ref="productRelationTable" :data="coupon.productRelationList" style="width: 100%;margin-top: 20px"
          border>
          <el-table-column label="상품명" align="center">
            <template #default="scope">{{ scope.row.productName }}</template>
          </el-table-column>
          <el-table-column label="상품 번호" align="center" width="120">
            <template #default="scope">NO.{{ scope.row.productSn }}</template>
          </el-table-column>
          <el-table-column label="작업" align="center" width="100">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="handleDeleteProductRelation(scope.$index)">삭제
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item label="메모:">
        <el-input class="input-width" type="textarea" :rows="5" placeholder="내용을 입력하세요" v-model="coupon.note">
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit()">제출</el-button>
        <el-button v-if="!isEdit" @click="resetForm()">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.input-width {
  width: 60%;
}
</style>

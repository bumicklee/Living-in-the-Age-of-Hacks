<script setup lang="ts">
import { ref, computed, onMounted, inject, type Ref } from 'vue'
import { ElButton, ElSwitch, ElCheckboxGroup, ElCheckbox, ElRadioGroup, ElRadioButton, ElInput, ElDatePicker, ElTable, ElTableColumn, ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getMemberLevelListAPI } from '@/apis/memberLevel'
import type { PmsProductParam } from '@/types/product'

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 이벤트 정의
const emit = defineEmits(['prev-step', 'next-step'])

// 계층 간 전달 데이터 조회
const compProductParam = inject('product-key') as Ref<PmsProductParam>

const getMemberLevelList = async () => {
  const res = await getMemberLevelListAPI({ defaultStatus: 0 })
  const memberPriceList = res.data.map(item => ({ memberLevelId: item.id, memberLevelName: item.name }))
  compProductParam.value.memberPriceList = memberPriceList
}

// 컴포넌트 마운트 시 호출
onMounted(() => {
  getMemberLevelList()
})

// 템플릿 참조
const productSaleForm = ref<FormInstance>()

// 날짜 비활성화 함수
const isDisabledDate = (time: Date) => {
  return time.getTime() < Date.now()
}

// 선택된 서비스 보장, 여러 개는 쉼표로 구분
const selectServiceList = computed({
  get() {
    if (!compProductParam.value.serviceIds) return []
    return compProductParam.value.serviceIds.split(',').map(item => Number(item))
  },
  set(newValue: number[]) {
    let serviceIds = ''
    if (newValue && newValue.length > 0) {
      for (let i = 0; i < newValue.length; i++) {
        serviceIds += newValue[i] + ','
      }
      if (serviceIds.endsWith(',')) {
        serviceIds = serviceIds.substring(0, serviceIds.length - 1)
      }
      compProductParam.value.serviceIds = serviceIds
    } else {
      compProductParam.value.serviceIds = ''
    }
  }
})

// 수량별 가격 삭제 작업 처리
const handleRemoveProductLadder = (index: number) => {
  const productLadderList = compProductParam.value.productLadderList
  if (!productLadderList) return
  if (productLadderList.length === 1) {
    productLadderList.pop()
    productLadderList.push({
      count: 0,
      discount: 0,
      price: 0
    })
  } else {
    productLadderList.splice(index, 1)
  }
}

// 수량별 가격 추가 작업 처리
const handleAddProductLadder = () => {
  const productLadderList = compProductParam.value.productLadderList
  if (productLadderList && productLadderList.length < 3) {
    productLadderList.push({
      count: 0,
      discount: 0,
      price: 0
    })
  } else {
    ElMessage({
      message: '최대 세 개까지만 추가할 수 있습니다',
      type: 'warning'
    })
  }
}

// 금액 할인 가격 삭제 작업 처리
const handleRemoveFullReduction = (index: number) => {
  const fullReductionList = compProductParam.value.productFullReductionList
  if (!fullReductionList) return
  if (fullReductionList.length === 1) {
    fullReductionList.pop()
    fullReductionList.push({
      fullPrice: 0,
      reducePrice: 0
    })
  } else {
    fullReductionList.splice(index, 1)
  }
}

// 금액 할인 가격 추가 작업 처리
const handleAddFullReduction = () => {
  const fullReductionList = compProductParam.value.productFullReductionList
  if (fullReductionList && fullReductionList.length < 3) {
    fullReductionList.push({
      fullPrice: 0,
      reducePrice: 0
    })
  } else {
    ElMessage({
      message: '최대 세 개까지만 추가할 수 있습니다',
      type: 'warning'
    })
  }
}

// 이전 단계 처리
const handlePrev = () => {
  emit('prev-step')
}

// 다음 단계 처리
const handleNext = () => {
  emit('next-step')
}
</script>

<template>
  <div style="margin-top: 50px">
    <el-form :model="compProductParam" ref="productSaleForm" label-width="120px" class="form-inner-container">
      <el-form-item label="포인트 지급:">
        <el-input v-model="compProductParam.giftPoint"></el-input>
      </el-form-item>
      <el-form-item label="성장 포인트 지급:">
        <el-input v-model="compProductParam.giftGrowth"></el-input>
      </el-form-item>
      <el-form-item label="포인트 구매 제한:">
        <el-input v-model="compProductParam.usePointLimit"></el-input>
      </el-form-item>
      <el-form-item label="예고 상품:">
        <el-switch v-model="compProductParam.previewStatus" :active-value="1" :inactive-value="0">
        </el-switch>
      </el-form-item>
      <el-form-item label="상품 판매 개시:">
        <el-switch v-model="compProductParam.publishStatus" :active-value="1" :inactive-value="0">
        </el-switch>
      </el-form-item>
      <el-form-item label="상품 추천:">
        <span style="margin-right: 10px">신상품</span>
        <el-switch v-model="compProductParam.newStatus" :active-value="1" :inactive-value="0">
        </el-switch>
        <span style="margin-left: 10px;margin-right: 10px">추천</span>
        <el-switch v-model="compProductParam.recommandStatus" :active-value="1" :inactive-value="0">
        </el-switch>
      </el-form-item>
      <el-form-item label="서비스 보장:">
        <el-checkbox-group v-model="selectServiceList">
          <el-checkbox :label="1">걱정 없는 반품</el-checkbox>
          <el-checkbox :label="2">빠른 환불</el-checkbox>
          <el-checkbox :label="3">무료 배송</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="상세 페이지 제목:">
        <el-input v-model="compProductParam.detailTitle"></el-input>
      </el-form-item>
      <el-form-item label="상세 페이지 설명:">
        <el-input v-model="compProductParam.detailDesc"></el-input>
      </el-form-item>
      <el-form-item label="상품 키워드:">
        <el-input v-model="compProductParam.keywords"></el-input>
      </el-form-item>
      <el-form-item label="상품 비고:">
        <el-input v-model="compProductParam.note" type="textarea" :autoSize="true"></el-input>
      </el-form-item>
      <el-form-item label="할인 방식 선택:">
        <el-radio-group v-model="compProductParam.promotionType">
          <el-radio-button :label="0">할인 없음</el-radio-button>
          <el-radio-button :label="1">특가 프로모션</el-radio-button>
          <el-radio-button :label="2">회원 가격</el-radio-button>
          <el-radio-button :label="3">구간별 가격</el-radio-button>
          <el-radio-button :label="4">금액 할인 가격</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-show="compProductParam.promotionType === 1">
        <div>
          <div>
            시작 시간:
            <el-date-picker v-model="compProductParam.promotionStartTime" type="datetime"
              :disabled-date="isDisabledDate" placeholder="시작 시간 선택">
            </el-date-picker>
          </div>
          <div class="littleMargin">
            종료 시간:
            <el-date-picker v-model="compProductParam.promotionEndTime" type="datetime" :disabled-date="isDisabledDate"
              placeholder="종료 시간 선택">
            </el-date-picker>
          </div>
          <div class="littleMargin">
            프로모션 가격:
            <el-input style="width: 220px" v-model="compProductParam.promotionPrice" placeholder="프로모션 가격 입력"></el-input>
          </div>
        </div>
      </el-form-item>
      <el-form-item v-show="compProductParam.promotionType === 2">
        <div>
          <div v-for="(item, index) in compProductParam.memberPriceList" :class="{ littleMargin: index !== 0 }"
            :key="item.id">
            {{ item.memberLevelName }}:
            <el-input v-model="item.memberPrice" style="width: 200px"></el-input>
          </div>
        </div>
      </el-form-item>
      <el-form-item v-show="compProductParam.promotionType === 3">
        <el-table :data="compProductParam.productLadderList" style="width: 80%" border>
          <el-table-column label="수량" align="center" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.count"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="할인" align="center" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.discount"></el-input>
            </template>
          </el-table-column>
          <el-table-column align="center" label="작업">
            <template #default="scope">
              <el-button type="text" @click="handleRemoveProductLadder(scope.$index)">삭제</el-button>
              <el-button type="text" @click="handleAddProductLadder()">추가</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item v-show="compProductParam.promotionType === 4">
        <el-table :data="compProductParam.productFullReductionList" style="width: 80%" border>
          <el-table-column label="이상" align="center" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.fullPrice"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="즉시 할인" align="center" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.reducePrice"></el-input>
            </template>
          </el-table-column>
          <el-table-column align="center" label="작업">
            <template #default="scope">
              <el-button type="text" @click="handleRemoveFullReduction(scope.$index)">삭제</el-button>
              <el-button type="text" @click="handleAddFullReduction()">추가</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%;text-align: center">
          <el-button @click="handlePrev">이전 단계, 상품 정보 입력</el-button>
          <el-button type="primary" @click="handleNext">다음 단계, 상품 속성 입력</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.littleMargin {
  margin-top: 10px
}
</style>

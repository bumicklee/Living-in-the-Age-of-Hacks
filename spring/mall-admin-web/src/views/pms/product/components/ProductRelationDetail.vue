<script setup lang="ts">
import { onMounted, inject, type Ref, computed, ref } from 'vue'
import { type TransferDataItem } from 'element-plus'
import type { PmsProductParam } from '@/types/product'
import { getSubjectListAllAPI } from '@/apis/subject'
import { getPrefrenceAreaListAllAPI } from '@/apis/prefrenceArea'
import type { ElTransferDataVo } from '@/types/common'

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 이벤트 정의
const emit = defineEmits(['prev-step', 'finish-commit'])

// 계층 간 전달 데이터 조회
const compProductParam = inject('product-key') as Ref<PmsProductParam>

// 전체 기획전 목록
const subjectList = ref<ElTransferDataVo[]>([])
// 전체 인기 추천 존 목록
const prefrenceAreaList = ref<ElTransferDataVo[]>([])
// 기획전 목록 조회
const getSubjectList = async () => {
  const res = await getSubjectListAllAPI()
  subjectList.value = res.data.map(item => ({
    label: item.title,
    key: item.id
  }))
}
// 엄선 상품 영역 목록 조회
const getPrefrenceAreaList = async () => {
  const res = await getPrefrenceAreaListAllAPI()
  prefrenceAreaList.value = res.data.map(item => ({
    label: item.name,
    key: item.id
  }))
}

// 데이터 초기화
onMounted(() => {
  getSubjectList()
  getPrefrenceAreaList()
})

// 연관 기획전
const selectSubject = computed({
  get: function () {
    return compProductParam.value.subjectProductRelationList?.map(item => item.subjectId!)
  },
  set: function (newValue) {
    compProductParam.value.subjectProductRelationList = []
    newValue?.forEach(item => {
      compProductParam.value.subjectProductRelationList!.push({ subjectId: item })
    })
  }
})

// 연관 엄선
const selectPrefrenceArea = computed({
  get: function () {
    return compProductParam.value.prefrenceAreaProductRelationList?.map(item => item.prefrenceAreaId!)
  },
  set: function (newValue) {
    compProductParam.value.prefrenceAreaProductRelationList = []
    newValue?.forEach(item => {
      compProductParam.value.prefrenceAreaProductRelationList!.push({ prefrenceAreaId: item })
    })
  }
})

// 필터 메서드
const filterMethod = (query: string, item: TransferDataItem) => {
  return item.label.indexOf(query) > -1
}

// 이전 단계 처리
const handlePrev = () => {
  emit('prev-step')
}

// 제출 처리 완료
const handleFinishCommit = async () => {
  emit('finish-commit', props.isEdit)
}
</script>

<template>
  <div style="margin-top: 50px">
    <el-form :model="compProductParam" ref="productRelationForm" label-width="120px" class="form-inner-container">
      <el-form-item label="연관 기획전:">
        <el-transfer style="display: inline-block" filterable :filter-method="filterMethod" filter-placeholder="기획전 이름을 입력하세요"
          v-model="selectSubject" :titles="['선택 대기', '선택됨']" :data="subjectList">
        </el-transfer>
      </el-form-item>
      <el-form-item label="연관 엄선:">
        <el-transfer style="display: inline-block" filterable :filter-method="filterMethod" filter-placeholder="엄선 상품 이름을 입력하세요"
          v-model="selectPrefrenceArea" :titles="['선택 대기', '선택됨']" :data="prefrenceAreaList">
        </el-transfer>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%;text-align: center">
          <el-button @click="handlePrev">이전 단계, 상품 속성 입력</el-button>
          <el-button type="primary" @click="handleFinishCommit">완료, 상품 제출</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped></style>

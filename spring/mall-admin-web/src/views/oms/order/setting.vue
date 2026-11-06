<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderSettingByIdAPI, orderSettingUpdateByIdAPI } from '@/apis/orderSetting'
import type { FormInstance, FormRules } from 'element-plus'
import type { OmsOrderSetting } from '@/types/orderSetting'

// 기본 주문 설정 데이터
const defaultOrderSetting = {
  id: 1,
  flashOrderOvertime: 0,
  normalOrderOvertime: 0,
  confirmOvertime: 0,
  finishOvertime: 0,
  commentOvertime: 0
}
// 주문 설정 데이터
const orderSetting = ref<OmsOrderSetting>(Object.assign({}, defaultOrderSetting))
// 상세 조회
const getDetail = async () => {
  const response = await getOrderSettingByIdAPI(orderSetting.value.id)
  orderSetting.value = response.data
}

// 컴포넌트 마운트 후 상세 조회
onMounted(() => {
  getDetail()
})

// 주문 설정 폼 참조
const orderSettingForm = ref<FormInstance>()
// 시간 검증 규칙
const checkTime = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    return callback(new Error('시간은 비워 둘 수 없습니다'))
  }
  const intValue = parseInt(value)
  if (!Number.isInteger(intValue)) {
    return callback(new Error('숫자 값을 입력하세요'))
  }
  callback()
}
// 폼 검증 규칙
const rules = ref<FormRules>({
  flashOrderOvertime: { validator: checkTime, trigger: 'blur' },
  normalOrderOvertime: { validator: checkTime, trigger: 'blur' },
  confirmOvertime: { validator: checkTime, trigger: 'blur' },
  finishOvertime: { validator: checkTime, trigger: 'blur' },
  commentOvertime: { validator: checkTime, trigger: 'blur' }
})

// 폼 제출 확인
const confirm = async () => {
  if (!orderSettingForm.value) return
  const valid = await orderSettingForm.value.validate()
  if (valid) {
    await ElMessageBox.confirm('수정 내용을 제출할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await orderSettingUpdateByIdAPI(1, orderSetting.value)
    ElMessage({
      type: 'success',
      message: '제출 성공!',
      duration: 1000
    })
  } else {
    ElMessage({
      message: '제출한 파라미터가 올바르지 않습니다',
      type: 'warning'
    })
    return false
  }
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form :model="orderSetting" ref="orderSettingForm" :rules="rules" label-width="150px">
      <el-form-item label="타임 세일 주문 초과:" prop="flashOrderOvertime">
        <el-input v-model="orderSetting.flashOrderOvertime" class="input-width">
          <template #append>분</template>
        </el-input>
        <span class="note-margin">미결제, 주문 자동 취소</span>
      </el-form-item>
      <el-form-item label="정상 주문 초과:" prop="normalOrderOvertime">
        <el-input v-model="orderSetting.normalOrderOvertime" class="input-width">
          <template #append>분</template>
        </el-input>
        <span class="note-margin">미결제, 주문 자동 취소</span>
      </el-form-item>
      <el-form-item label="발송 후 경과:" prop="confirmOvertime">
        <el-input v-model="orderSetting.confirmOvertime" class="input-width">
          <template #append>일</template>
        </el-input>
        <span class="note-margin">미수령, 주문 자동 완료</span>
      </el-form-item>
      <el-form-item label="주문 완료 후 경과:" prop="finishOvertime">
        <el-input v-model="orderSetting.finishOvertime" class="input-width">
          <template #append>일</template>
        </el-input>
        <span class="note-margin">자동 거래 종료, A/S 신청 불가</span>
      </el-form-item>
      <el-form-item label="주문 완료 후 경과:" prop="commentOvertime">
        <el-input v-model="orderSetting.commentOvertime" class="input-width">
          <template #append>일</template>
        </el-input>
        <span class="note-margin">자동 별 5개 리뷰</span>
      </el-form-item>
      <el-form-item>
        <el-button @click="confirm()" type="primary">제출</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.input-width {
  width: 50%
}

.note-margin {
  margin-left: 15px
}
</style>

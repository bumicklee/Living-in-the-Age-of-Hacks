<script setup lang="ts">
import { ref, computed } from 'vue'

// 컴포넌트의 정의props
const props = defineProps({
  modelValue: Boolean
})

// 컴포넌트의 정의emits
const emit = defineEmits(['update:modelValue'])

// 기본 배송 목록 데이터
const defaultLogisticsList = [
  { name: '주문이 접수되었습니다. 결제를 기다리고 있습니다', time: '2017-04-01 12:00:00 ' },
  { name: '주문 결제 성공', time: '2017-04-01 12:00:00 ' },
  { name: '베이징시에서 다음 거점 스캔 완료, 결제 대기 중', time: '2017-04-01 12:00:00 ' },
  { name: '광둥 선전 물류센터에서 하차 스캔 완료, 결제 대기 중', time: '2017-04-01 12:00:00 ' },
  { name: '광둥 선전 지점에서 발송 스캔 완료', time: '2017-04-01 12:00:00 ' },
  { name: '목적지 지점 광둥 선전 회사에 도착했으며, 택배가 곧 배송될 예정입니다', time: '2017-04-01 12:00:00 ' },
  { name: '주문이 수령 처리되었습니다. 다시 이용해 주세요', time: '2017-04-01 12:00:00 ' }
]

// 배송 목록 데이터
const logisticsList = ref(Object.assign([], defaultLogisticsList))

// 대화상자 표시를 제어하는 계산 속성
const visible = computed({
  get() {
    return props.modelValue
  },
  set(visible) {
    emit('update:modelValue', visible)
  }
})

// input 이벤트 발생
const emitInput = (val: boolean) => {
  emit('update:modelValue', val)
}

// 대화 상자 닫기 처리
const handleClose = () => {
  emitInput(false)
}
</script>

<template>
  <el-dialog title="주문 추적" v-model="visible" :before-close="handleClose" width="40%">
    <el-steps direction="vertical" :active="6" finish-status="success" space="50px">
      <el-step v-for="item in logisticsList" :key="item.name" :title="item.name" :description="item.time"></el-step>
    </el-steps>
  </el-dialog>
</template>

<style></style>

import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { OmsOrder } from '@/types/order'

export const useOrderStore = defineStore('order', () => {
  // 일괄 발송 시 임시 저장하는 주문 목록(페이지 간 데이터 전달용)
  const deliverOrderList = ref<OmsOrder[]>([])

  // 전달할 주문 목록 설정
  const setDeliverOrderList = (list: OmsOrder[]) => {
    deliverOrderList.value = list
  }

  return {
    deliverOrderList,
    setDeliverOrderList,
  }
})

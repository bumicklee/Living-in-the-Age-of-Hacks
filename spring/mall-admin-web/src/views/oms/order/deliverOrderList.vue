<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { orderUpdateDeliveryAPI } from '@/apis/order'
import type { OmsOrder, OmsOrderDeliveryParam } from '@/types/order'
import { useOrderStore } from '@/stores/order'

// 라우트 객체 조회
const router = useRouter()
// 조회 주문store
const orderStore = useOrderStore()

// 기본 택배사 옵션
const defaultLogisticsCompanies = ["SF 익스프레스", "위안퉁 택배", "중퉁 택배", "윈다 택배"]

// 발송 주문 목록 데이터
const list = ref<OmsOrder[]>([])
// 택배사 옵션
const companyOptions = ref<string[]>(defaultLogisticsCompanies)

// 주문 객체로 상세 주소 조회
const fortmatAddress = (order: OmsOrder) => {
  return order.receiverProvince + order.receiverCity + order.receiverRegion + order.receiverDetailAddress
}

// 컴포넌트 마운트 후 데이터 초기화
onMounted(() => {
  list.value = orderStore.deliverOrderList
  // store의 데이터 비우기
  orderStore.setDeliverOrderList([])
})

// 작업 취소
const cancel = () => {
  router.back()
}

// 발송 확인 작업
const confirm = async () => {
  try {
    await ElMessageBox.confirm('발송 처리할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    const deliveryParamList: OmsOrderDeliveryParam[] = list.value.map(item => ({
      orderId: item.id,
      deliverySn: item.orderSn,
      deliveryCompany: item.deliveryCompany
    }))
    await orderUpdateDeliveryAPI(deliveryParamList)
    router.back()
    ElMessage({
      type: 'success',
      message: '발송 성공!'
    })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage({
        type: 'info',
        message: '발송 취소됨'
      })
    }
  }
}
</script>

<template>
  <div class="app-container">
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>발송 목록</span>
    </el-card>
    <div class="table-container">
      <el-table ref="deliverOrderTable" style="width: 100%;" :data="list" border>
        <el-table-column label="주문 번호" width="180" align="center">
          <template #default="scope">{{ scope.row.orderSn }}</template>
        </el-table-column>
        <el-table-column label="수령인" width="150" align="center">
          <template #default="scope">{{ scope.row.receiverName }}</template>
        </el-table-column>
        <el-table-column label="휴대폰 번호" width="160" align="center">
          <template #default="scope">{{ scope.row.receiverPhone }}</template>
        </el-table-column>
        <el-table-column label="우편번호" width="160" align="center">
          <template #default="scope">{{ scope.row.receiverPostCode }}</template>
        </el-table-column>
        <el-table-column label="배송지" align="center">
          <template #default="scope">{{ fortmatAddress(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="배송 방식" width="200" align="center">
          <template #default="scope">
            <el-select placeholder="택배사를 선택하세요" v-model="scope.row.deliveryCompany">
              <el-option v-for="item in companyOptions" :key="item" :label="item" :value="item">
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="운송장 번호" width="180" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.deliverySn"></el-input>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 15px;text-align: center">
        <el-button @click="cancel">취소</el-button>
        <el-button @click="confirm" type="primary">확인</el-button>
      </div>
    </div>
  </div>
</template>

<style></style>

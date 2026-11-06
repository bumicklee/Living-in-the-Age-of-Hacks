<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getOrderListAPI, orderUpdateCloseAPI, orderDeleteByIdsAPI } from '@/apis/order'
import LogisticsDialog from '@/views/oms/order/components/logisticsDialog.vue'
import { formatDateTime } from '@/utils/datetime'
import type { OmsOrder, OrderQueryParam } from '@/types/order'
import { useOrderStore } from '@/stores/order'

// 라우트 객체 조회
const router = useRouter()

// 주문 저장소 조회store
const orderStore = useOrderStore()

// 주문 목록 조회 파라미터
const listQuery = ref<OrderQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 주문 목록 데이터
const list = ref<OmsOrder[]>([])
// 테이블 데이터 로딩 진행 표시줄
const listLoading = ref(true)
// 페이지네이션 컴포넌트 파라미터
const total = ref(0)
// 주문 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getOrderListAPI(listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error('주문 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 후 목록 정보 초기화
onMounted(() => {
  getList()
})

// 테이블에서 선택된 행
const multipleSelection = ref<OmsOrder[]>([])
// 일괄 작업 유형
const operateType = ref<number>()

// 주문 종료 대화상자 관련 데이터
const closeOrderData = ref({
  dialogVisible: false,
  content: '',
  orderIds: [] as number[]
})

// 배송 대화상자 표시 여부
const logisticsDialogVisible = ref(false)

// 주문 상태 옵션
const statusOptions = [
  {
    label: '결제 대기',
    value: 0
  },
  {
    label: '배송 대기',
    value: 1
  },
  {
    label: '발송됨',
    value: 2
  },
  {
    label: '완료',
    value: 3
  },
  {
    label: '닫힘',
    value: 4
  }
]

// 주문 유형 옵션
const orderTypeOptions = [
  {
    label: '정상 주문',
    value: 0
  },
  {
    label: '타임 세일 주문',
    value: 1
  }
]

// 주문 출처 옵션
const sourceTypeOptions = [
  {
    label: 'PC주문',
    value: 0
  },
  {
    label: 'APP주문',
    value: 1
  }
]

// 일괄 작업 옵션
const operateOptions = [
  {
    label: "일괄 발송",
    value: 1
  },
  {
    label: "주문 종료",
    value: 2
  },
  {
    label: "주문 삭제",
    value: 3
  }
]

// 결제 수단 포맷
const formatPayType = (value: number) => {
  if (value === 1) {
    return '알리페이'
  } else if (value === 2) {
    return '위챗'
  } else {
    return '미결제'
  }
}

// 주문 출처 포맷
const formatSourceType = (value: number) => {
  if (value === 1) {
    return 'APP주문'
  } else {
    return 'PC주문'
  }
}

// 주문 상태 포맷
const formatStatus = (value: number) => {
  if (value === 1) {
    return '배송 대기'
  } else if (value === 2) {
    return '발송됨'
  } else if (value === 3) {
    return '완료'
  } else if (value === 4) {
    return '닫힘'
  } else if (value === 5) {
    return '무효 주문'
  } else {
    return '결제 대기'
  }
}

// 검색 초기화 처리
const handleResetSearch = () => {
  listQuery.value = { pageNum: 1, pageSize: 10 }
}

// 검색 목록 처리
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 테이블 선택 상태 변경 처리
const handleSelectionChange = (val: OmsOrder[]) => {
  multipleSelection.value = val
}

// 주문 조회 처리
const handleViewOrder = (index: number, row: OmsOrder) => {
  router.push({ path: '/oms/orderDetail', query: { id: row.id } })
}

// 주문 닫기 처리
const handleCloseOrder = (index: number, row: OmsOrder) => {
  closeOrderData.value.dialogVisible = true
  closeOrderData.value.orderIds = [row.id!]
}

// 주문 발송 처리
const handleDeliveryOrder = (index: number, row: OmsOrder) => {
  orderStore.setDeliverOrderList([row])
  router.push({ path: '/oms/deliverOrderList' })
}

// 배송 조회 처리
const handleViewLogistics = (index: number, row: OmsOrder) => {
  logisticsDialogVisible.value = true
  console.log(index, row)
}

// 주문 삭제 처리
const handleDeleteOrder = async (index: number, row: OmsOrder) => {
  const ids = [row.id!]
  await deleteOrderFn(ids)
}

// 일괄 작업 처리
const handleBatchOperate = async () => {
  if (!multipleSelection.value || multipleSelection.value.length < 1) {
    ElMessage({
      message: '작업할 주문을 선택하세요',
      type: 'warning',
      duration: 1000
    })
    return
  }
  if (operateType.value === 1) {
    // 일괄 발송
    const listItems = multipleSelection.value.filter(item => item.status === 1)
    if (!listItems || listItems.length < 1) {
      ElMessage({
        message: '선택한 주문 중 발송 가능한 주문이 없습니다',
        type: 'warning',
        duration: 1000
      })
      return
    }
    orderStore.setDeliverOrderList(listItems)
    router.push({ path: '/oms/deliverOrderList' })
  } else if (operateType.value === 2) {
    // 주문 종료
    closeOrderData.value.orderIds = multipleSelection.value.filter(item => item.status === 0)
      .map(item => item.id)
    closeOrderData.value.dialogVisible = true
  } else if (operateType.value === 3) {
    // 주문 삭제
    const ids = multipleSelection.value.filter(item => item.status === 4)
      .map(item => item.id)
    await deleteOrderFn(ids)
  }
}

// 페이지당 건수 변경 처리
const handleSizeChange = (val: number) => {
  listQuery.value.pageNum = 1
  listQuery.value.pageSize = val
  getList()
}

// 현재 페이지 변경 처리
const handleCurrentChange = (val: number) => {
  listQuery.value.pageNum = val
  getList()
}

// 주문 닫기 확인 처리
const handleCloseOrderConfirm = async () => {
  if (!closeOrderData.value.content) {
    ElMessage({
      message: '작업 메모는 비워 둘 수 없습니다',
      type: 'warning',
      duration: 1000
    })
    return
  }
  const orderIds = closeOrderData.value.orderIds.join(',')
  await orderUpdateCloseAPI({ ids: orderIds, note: closeOrderData.value.content })
  closeOrderData.value.orderIds = []
  closeOrderData.value.dialogVisible = false
  getList()
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

// 주문 삭제 함수
const deleteOrderFn = async (ids: number[]) => {
  await ElMessageBox.confirm('삭제할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await orderDeleteByIdsAPI({ ids: ids.join(',') })
  ElMessage({
    message: '삭제 성공!',
    type: 'success',
    duration: 1000
  })
  getList()
}
</script>

<template>
  <div class="app-container">
    <el-card class="filter-container" shadow="never">
      <div>
        <el-icon class="el-icon-middle">
          <Search />
        </el-icon>
        <span>필터 검색</span>
        <el-button style="float:right" type="primary" @click="handleSearchList()">
          조회 검색
        </el-button>
        <el-button style="float:right;margin-right: 15px" @click="handleResetSearch()">
          초기화
        </el-button>
      </div>
      <div style="margin-top: 20px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="검색어 입력:">
            <el-input v-model="listQuery.orderSn" class="input-width" placeholder="주문 번호"></el-input>
          </el-form-item>
          <el-form-item label="수령인:">
            <el-input v-model="listQuery.receiverKeyword" class="input-width" placeholder="수령인 이름/휴대폰 번호"></el-input>
          </el-form-item>
          <el-form-item label="제출 시간:">
            <el-date-picker class="input-width" v-model="listQuery.createTime" value-format="yyyy-MM-dd" type="date"
              placeholder="시간을 선택하세요">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="주문 상태:">
            <el-select v-model="listQuery.status" class="input-width" placeholder="전체" clearable>
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="주문 분류:">
            <el-select v-model="listQuery.orderType" class="input-width" placeholder="전체" clearable>
              <el-option v-for="item in orderTypeOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="주문 출처:">
            <el-select v-model="listQuery.sourceType" class="input-width" placeholder="전체" clearable>
              <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
    </el-card>
    <div class="table-container">
      <el-table ref="orderTable" :data="list" style="width: 100%;" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="80" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="주문 번호" width="180" align="center">
          <template #default="scope">{{ scope.row.orderSn }}</template>
        </el-table-column>
        <el-table-column label="제출 시간" width="180" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="사용자 계정" align="center">
          <template #default="scope">{{ scope.row.memberUsername }}</template>
        </el-table-column>
        <el-table-column label="주문 금액" width="120" align="center">
          <template #default="scope">₩{{ scope.row.totalAmount }}</template>
        </el-table-column>
        <el-table-column label="결제 수단" width="120" align="center">
          <template #default="scope">{{ formatPayType(scope.row.payType) }}</template>
        </el-table-column>
        <el-table-column label="주문 출처" width="120" align="center">
          <template #default="scope">{{ formatSourceType(scope.row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="주문 상태" width="120" align="center">
          <template #default="scope">{{ formatStatus(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleViewOrder(scope.$index, scope.row)">주문 보기</el-button>
            <el-button size="small" @click="handleCloseOrder(scope.$index, scope.row)"
              v-show="scope.row.status === 0">주문 종료</el-button>
            <el-button size="small" @click="handleDeliveryOrder(scope.$index, scope.row)"
              v-show="scope.row.status === 1">주문 발송</el-button>
            <el-button size="small" @click="handleViewLogistics(scope.$index, scope.row)"
              v-show="scope.row.status === 2 || scope.row.status === 3">주문 추적</el-button>
            <el-button size="small" type="danger" @click="handleDeleteOrder(scope.$index, scope.row)"
              v-show="scope.row.status === 4">주문 삭제</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="batch-operate-container">
      <el-select v-model="operateType" placeholder="일괄 작업">
        <el-option v-for="item in operateOptions" :key="item.value" :label="item.label" :value="item.value">
        </el-option>
      </el-select>
      <el-button style="margin-left: 20px" class="search-button" @click="handleBatchOperate()" type="primary">
        확인
      </el-button>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" v-model:current-page="listQuery.pageNum"
        :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="total">
      </el-pagination>
    </div>
    <el-dialog title="주문 종료" v-model="closeOrderData.dialogVisible" width="30%">
      <span style="vertical-align: top">작업 메모:</span>
      <el-input style="width: 80%" type="textarea" :rows="5" placeholder="내용을 입력하세요" v-model="closeOrderData.content">
      </el-input>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeOrderData.dialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleCloseOrderConfirm">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <logistics-dialog v-model="logisticsDialogVisible"></logistics-dialog>
  </div>
</template>

<style scoped>
.input-width {
  width: 203px
}
</style>

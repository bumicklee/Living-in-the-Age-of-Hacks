<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type CascaderOption } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import { getOrderDetailByIdAPI, orderUpdateReceiverInfoAPI, orderUpdateMoneyInfoAPI, orderUpdateCloseAPI, orderUpdateNoteAPI, orderDeleteByIdsAPI } from '@/apis/order'
import LogisticsDialog from '@/views/oms/order/components/logisticsDialog.vue'
import type { OmsOrder, OmsOrderDetail, OmsReceiverInfoParam } from '@/types/order'
import { formatDateTime } from '@/utils/datetime'
import { pcaTextArr } from 'element-china-area-data'

// 라우트 객체 조회
const router = useRouter()
const route = useRoute()

// 주문ID
const id = ref<number>()
// 주문 상세 데이터
const order = ref<OmsOrderDetail>({} as OmsOrderDetail)

// 컴포넌트 마운트 후 주문 상세 조회
onMounted(async () => {
  id.value = Number(route.query.id)
  const res = await getOrderDetailByIdAPI(id.value!)
  order.value = res.data
})

// 수령인 정보 수정 대화상자 표시 여부
const receiverDialogVisible = ref(false)
// 수령인 정보
const receiverInfo = ref<OmsReceiverInfoParam>({} as OmsReceiverInfoParam)
// 현재 선택된 시/도, 시/군/구, 읍/면/동 3단계 연동
const selectedRegions = ref<string[]>([])

// 비용 정보 수정 대화상자 표시 여부
const moneyDialogVisible = ref(false)
// 비용 정보
const moneyInfo = ref({ orderId: 0, freightAmount: 0, discountAmount: 0, status: 0 })

// 쪽지 전송 대화 상자 표시 여부
const messageDialogVisible = ref(false)
// 쪽지 내용
const message = ref({ title: '', content: '' })

// 주문 종료 대화상자 표시 여부
const closeDialogVisible = ref(false)
// 주문 종료 정보
const closeInfo = ref({ note: '', id: 0 })

// 주문 메모 대화 상자 표시 여부
const markOrderDialogVisible = ref(false)
// 주문 메모 정보
const markInfo = ref({ id: 0, note: '' })

// 배송 대화상자 표시 여부
const logisticsDialogVisible = ref(false)

// 빈 값 포맷
const formatNull = (value: string | undefined) => {
  if (!value) {
    return '없음'
  } else {
    return value
  }
}

// 긴 텍스트 포맷
const formatLongText = (value: string | undefined) => {
  if (!value) {
    return '없음'
  } else if (value.length > 8) {
    return value.substring(0, 8) + '...'
  } else {
    return value
  }
}

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

// 주문 유형 포맷
const formatOrderType = (value: number) => {
  if (value === 1) {
    return '타임 세일 주문'
  } else {
    return '정상 주문'
  }
}

// 주소 포맷
const formatAddress = (order: OmsOrder) => {
  let str = order.receiverProvince || ''
  if (order.receiverCity != null) {
    str += "  " + order.receiverCity
  }
  str += "  " + (order.receiverRegion || '')
  str += "  " + (order.receiverDetailAddress || '')
  return str
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

// 결제 상태 포맷
const formatPayStatus = (value: number) => {
  if (value === 0) {
    return '미결제'
  } else if (value === 4) {
    return '환불 완료'
  } else {
    return '결제 완료'
  }
}

// 배송 상태 포맷
const formatDeliverStatus = (value: number) => {
  if (value === 0 || value === 1) {
    return '미발송'
  } else {
    return '발송됨'
  }
}

// 상품 속성 포맷
const formatProductAttr = (value: string) => {
  if (value == null) {
    return ''
  } else {
    const attr = JSON.parse(value)
    let result = ''
    for (let i = 0; i < attr.length; i++) {
      result += attr[i].key
      result += ":"
      result += attr[i].value
      result += ";"
    }
    return result
  }
}

// 단계 상태 포맷
const formatStepStatus = (value: number) => {
  if (value === 1) {
    //배송 대기
    return 2
  } else if (value === 2) {
    //발송됨
    return 3
  } else if (value === 3) {
    //완료
    return 4
  } else {
    //결제 대기, 닫힘, 무제한 주문
    return 1
  }
}

// 지역 선택
const onSelectRegionChange = () => {
  receiverInfo.value.receiverProvince = selectedRegions.value[0]
  receiverInfo.value.receiverCity = selectedRegions.value[1]
  receiverInfo.value.receiverRegion = selectedRegions.value[2]
}

// 수령인 정보 수정 대화상자 표시
const showUpdateReceiverDialog = () => {
  receiverDialogVisible.value = true
  receiverInfo.value = {
    orderId: order.value.id,
    receiverName: order.value.receiverName,
    receiverPhone: order.value.receiverPhone,
    receiverPostCode: order.value.receiverPostCode,
    receiverDetailAddress: order.value.receiverDetailAddress,
    receiverProvince: order.value.receiverProvince,
    receiverCity: order.value.receiverCity,
    receiverRegion: order.value.receiverRegion,
    status: order.value.status
  }
  // 주소 선택기 데이터 초기화
  selectedRegions.value = []
  selectedRegions.value[0] = receiverInfo.value.receiverProvince!
  selectedRegions.value[1] = receiverInfo.value.receiverCity!
  selectedRegions.value[2] = receiverInfo.value.receiverRegion!
}

// 수령인 정보 갱신 처리
const handleUpdateReceiverInfo = async () => {
  await ElMessageBox.confirm('배송 정보를 수정할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await orderUpdateReceiverInfoAPI(receiverInfo.value)
  receiverDialogVisible.value = false
  ElMessage({
    type: 'success',
    message: '수정 성공!'
  })
  const response = await getOrderDetailByIdAPI(id.value!)
  order.value = response.data
}

// 비용 정보 수정 대화상자 표시
const showUpdateMoneyDialog = () => {
  moneyDialogVisible.value = true
  moneyInfo.value.orderId = order.value.id
  moneyInfo.value.freightAmount = order.value.freightAmount
  moneyInfo.value.discountAmount = order.value.discountAmount
  moneyInfo.value.status = order.value.status
}

// 비용 정보 갱신 처리
const handleUpdateMoneyInfo = async () => {
  await ElMessageBox.confirm('비용 정보를 수정할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await orderUpdateMoneyInfoAPI(moneyInfo.value)
  moneyDialogVisible.value = false
  ElMessage({
    type: 'success',
    message: '수정 성공!'
  })
  const response = await getOrderDetailByIdAPI(id.value!)
  order.value = response.data
}

// 인앱 메시지 발송 대화상자 표시
const showMessageDialog = () => {
  messageDialogVisible.value = true
  message.value.title = ''
  message.value.content = ''
}

// 쪽지 전송 처리
const handleSendMessage = async () => {
  await ElMessageBox.confirm('인앱 메시지를 발송할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  console.log('쪽지 기능은 아직 구현되지 않아 발송을 모의로 처리합니다..')
  messageDialogVisible.value = false
  ElMessage({
    type: 'success',
    message: '전송 성공!'
  })
}

// 주문 닫기 대화상자 표시
const showCloseOrderDialog = () => {
  closeDialogVisible.value = true
  closeInfo.value.note = ''
  closeInfo.value.id = id.value!
}

// 주문 닫기 처리
const handleCloseOrder = async () => {
  try {
    await ElMessageBox.confirm('닫을까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await orderUpdateCloseAPI({ ids: closeInfo.value.id.toString(), note: closeInfo.value.note })
    closeDialogVisible.value = false
    ElMessage({
      type: 'success',
      message: '주문 종료 성공!'
    })

    const response = await getOrderDetailByIdAPI(id.value!)
    order.value = response.data
  } catch (error) {
    if (error !== 'cancel') {
      console.error('주문 종료 실패:', error)
    }
  }
}

// 주문 메모 대화상자 표시
const showMarkOrderDialog = () => {
  markOrderDialogVisible.value = true
  markInfo.value.id = id.value!
  markInfo.value.note = ''
}

// 주문 메모 처리
const handleMarkOrder = async () => {
  await ElMessageBox.confirm('주문에 메모를 남길까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await orderUpdateNoteAPI({ id: markInfo.value.id, note: markInfo.value.note, status: order.value.status })
  markOrderDialogVisible.value = false
  ElMessage({
    type: 'success',
    message: '주문 메모 성공!'
  })
  const response = await getOrderDetailByIdAPI(id.value!)
  order.value = response.data
}

// 주문 삭제 처리
const handleDeleteOrder = async () => {
  try {
    await ElMessageBox.confirm('삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await orderDeleteByIdsAPI({ ids: id.value!.toString() })
    ElMessage({
      message: '삭제 성공!',
      type: 'success',
      duration: 1000
    })
    router.back()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('주문 삭제 실패:', error)
    }
  }
}

// 배송 대화상자 표시
const showLogisticsDialog = () => {
  logisticsDialogVisible.value = true
}

</script>

<template>
  <div class="detail-container">
    <div>
      <el-steps :active="formatStepStatus(order.status)" finish-status="success" align-center>
        <el-step title="주문 제출" :description="order.createTime ? formatDateTime(order.createTime) : ''"></el-step>
        <el-step title="결제 주문" :description="order.paymentTime ? formatDateTime(order.paymentTime) : ''"></el-step>
        <el-step title="플랫폼 발송" :description="order.deliveryTime ? formatDateTime(order.deliveryTime) : ''"></el-step>
        <el-step title="수취 확인" :description="order.receiveTime ? formatDateTime(order.receiveTime) : ''"></el-step>
        <el-step title="후기 작성 완료" :description="order.commentTime ? formatDateTime(order.commentTime) : ''"></el-step>
      </el-steps>
    </div>
    <el-card shadow="never" style="margin-top: 15px">
      <div class="operate-container">
        <el-icon class="color-danger el-icon-middle" style="margin-left: 15px;">
          <Warning />
        </el-icon>
        <span class="color-danger">현재 주문 상태:{{ formatStatus(order.status) }}</span>
        <div class="operate-button-container" v-show="order.status === 0">
          <el-button size="small" @click="showUpdateReceiverDialog">수령인 정보 수정</el-button>
          <el-button size="small" @click="showUpdateMoneyDialog">비용 정보 수정</el-button>
          <el-button size="small" @click="showMessageDialog">쪽지 전송</el-button>
          <el-button size="small" @click="showCloseOrderDialog">주문 종료</el-button>
          <el-button size="small" @click="showMarkOrderDialog">주문 메모</el-button>
        </div>
        <div class="operate-button-container" v-show="order.status === 1">
          <el-button size="small" @click="showUpdateReceiverDialog">수령인 정보 수정</el-button>
          <el-button size="small" @click="showMessageDialog">쪽지 전송</el-button>
          <el-button size="small">주문 취소</el-button>
          <el-button size="small" @click="showMarkOrderDialog">주문 메모</el-button>
        </div>
        <div class="operate-button-container" v-show="order.status === 2 || order.status === 3">
          <el-button size="small" @click="showLogisticsDialog">주문 추적</el-button>
          <el-button size="small" @click="showMessageDialog">쪽지 전송</el-button>
          <el-button size="small" @click="showMarkOrderDialog">주문 메모</el-button>
        </div>
        <div class="operate-button-container" v-show="order.status === 4">
          <el-button size="small" @click="handleDeleteOrder">주문 삭제</el-button>
          <el-button size="small" @click="showMarkOrderDialog">주문 메모</el-button>
        </div>
      </div>
      <!-- 나머지 템플릿 내용은 그대로이며, 지면 관계상 대부분의 템플릿 코드는 생략했습니다 -->
      <!-- 원본 파일을 참고하여 템플릿 내용을 완성해 주세요 -->
      <div style="margin-top: 20px">
        <svg-icon icon-class="marker" style="color: #606266"></svg-icon>
        <span class="font-small">기본 정보</span>
      </div>
      <div class="table-layout">
        <el-row>
          <el-col :span="4" class="table-cell-title">주문 번호</el-col>
          <el-col :span="4" class="table-cell-title">발송 번호</el-col>
          <el-col :span="4" class="table-cell-title">사용자 계정</el-col>
          <el-col :span="4" class="table-cell-title">결제 수단</el-col>
          <el-col :span="4" class="table-cell-title">주문 출처</el-col>
          <el-col :span="4" class="table-cell-title">주문 유형</el-col>
        </el-row>
        <el-row>
          <el-col :span="4" class="table-cell">{{ order.orderSn }}</el-col>
          <el-col :span="4" class="table-cell">없음</el-col>
          <el-col :span="4" class="table-cell">{{ order.memberUsername }}</el-col>
          <el-col :span="4" class="table-cell">{{ formatPayType(order.payType) }}</el-col>
          <el-col :span="4" class="table-cell">{{ formatSourceType(order.sourceType) }}</el-col>
          <el-col :span="4" class="table-cell">{{ formatOrderType(order.orderType) }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="4" class="table-cell-title">배송 방식</el-col>
          <el-col :span="4" class="table-cell-title">운송장 번호</el-col>
          <el-col :span="4" class="table-cell-title">자동 수취 확인 시간</el-col>
          <el-col :span="4" class="table-cell-title">주문으로 받을 수 있는 포인트</el-col>
          <el-col :span="4" class="table-cell-title">주문으로 받을 수 있는 성장 포인트</el-col>
          <el-col :span="4" class="table-cell-title">이벤트 정보</el-col>
        </el-row>
        <el-row>
          <el-col :span="4" class="table-cell">{{ formatNull(order.deliveryCompany) }}</el-col>
          <el-col :span="4" class="table-cell">{{ formatNull(order.deliverySn) }}</el-col>
          <el-col :span="4" class="table-cell">{{ order.autoConfirmDay }}일</el-col>
          <el-col :span="4" class="table-cell">{{ order.integration }}</el-col>
          <el-col :span="4" class="table-cell">{{ order.growth }}</el-col>
          <el-col :span="4" class="table-cell">
            <el-popover placement="top-start" title="이벤트 정보" width="200" trigger="hover" :content="order.promotionInfo">
              <template v-slot:reference>
                <span>{{ formatLongText(order.promotionInfo) }}</span>
              </template>
            </el-popover>
          </el-col>
        </el-row>
      </div>
      <div style="margin-top: 20px">
        <svg-icon icon-class="marker" style="color: #606266"></svg-icon>
        <span class="font-small">수령인 정보</span>
      </div>
      <div class="table-layout">
        <el-row>
          <el-col :span="6" class="table-cell-title">수령인</el-col>
          <el-col :span="6" class="table-cell-title">휴대폰 번호</el-col>
          <el-col :span="6" class="table-cell-title">우편번호</el-col>
          <el-col :span="6" class="table-cell-title">배송지</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell">{{ order.receiverName }}</el-col>
          <el-col :span="6" class="table-cell">{{ order.receiverPhone }}</el-col>
          <el-col :span="6" class="table-cell">{{ order.receiverPostCode }}</el-col>
          <el-col :span="6" class="table-cell">{{ formatAddress(order) }}</el-col>
        </el-row>
      </div>
      <div style="margin-top: 20px">
        <svg-icon icon-class="marker" style="color: #606266"></svg-icon>
        <span class="font-small">상품 정보</span>
      </div>
      <el-table ref="orderItemTable" :data="order.orderItemList" style="width: 100%;margin-top: 20px" border>
        <el-table-column label="상품 이미지" width="120" align="center">
          <template v-slot="scope">
            <img :src="scope.row.productPic" style="height: 80px">
          </template>
        </el-table-column>
        <el-table-column label="상품명" align="center">
          <template v-slot="scope">
            <p>{{ scope.row.productName }}</p>
            <p>브랜드:{{ scope.row.productBrand }}</p>
          </template>
        </el-table-column>
        <el-table-column label="가격/상품번호" width="160" align="center">
          <template v-slot="scope">
            <p>가격: ₩{{ scope.row.productPrice }}</p>
            <p>상품 번호:{{ scope.row.productSn }}</p>
          </template>
        </el-table-column>
        <el-table-column label="속성" width="160" align="center">
          <template v-slot="scope">
            {{ formatProductAttr(scope.row.productAttr) }}
          </template>
        </el-table-column>
        <el-table-column label="수량" width="120" align="center">
          <template v-slot="scope">
            {{ scope.row.productQuantity }}
          </template>
        </el-table-column>
        <el-table-column label="소계" width="120" align="center">
          <template v-slot="scope">
            ₩{{ scope.row.productPrice * scope.row.productQuantity }}
          </template>
        </el-table-column>
      </el-table>
      <div style="float: right;margin: 20px">
        합계:<span class="color-danger">₩{{ order.totalAmount }}</span>
      </div>
      <div style="margin-top: 60px">
        <svg-icon icon-class="marker" style="color: #606266"></svg-icon>
        <span class="font-small">비용 정보</span>
      </div>
      <div class="table-layout">
        <el-row>
          <el-col :span="6" class="table-cell-title">상품 합계</el-col>
          <el-col :span="6" class="table-cell-title">배송비</el-col>
          <el-col :span="6" class="table-cell-title">쿠폰</el-col>
          <el-col :span="6" class="table-cell-title">포인트 차감</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell">₩{{ order.totalAmount }}</el-col>
          <el-col :span="6" class="table-cell">₩{{ order.freightAmount }}</el-col>
          <el-col :span="6" class="table-cell">-₩{{ order.couponAmount }}</el-col>
          <el-col :span="6" class="table-cell">-₩{{ order.integrationAmount }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell-title">이벤트 할인</el-col>
          <el-col :span="6" class="table-cell-title">할인 금액</el-col>
          <el-col :span="6" class="table-cell-title">주문 총 금액</el-col>
          <el-col :span="6" class="table-cell-title">결제 예정 금액</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell">-₩{{ order.promotionAmount }}</el-col>
          <el-col :span="6" class="table-cell">-₩{{ order.discountAmount }}</el-col>
          <el-col :span="6" class="table-cell">
            <span class="color-danger">₩{{ order.totalAmount + order.freightAmount }}</span>
          </el-col>
          <el-col :span="6" class="table-cell">
            <span class="color-danger">₩{{ order.payAmount + order.freightAmount - order.discountAmount }}</span>
          </el-col>
        </el-row>
      </div>
      <div style="margin-top: 20px">
        <svg-icon icon-class="marker" style="color: #606266"></svg-icon>
        <span class="font-small">작업 정보</span>
      </div>
      <el-table style="margin-top: 20px;width: 100%" ref="orderHistoryTable" :data="order.historyList" border>
        <el-table-column label="작업자" width="120" align="center">
          <template v-slot="scope">
            {{ scope.row.operateMan }}
          </template>
        </el-table-column>
        <el-table-column label="작업 시간" width="160" align="center">
          <template v-slot="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="주문 상태" width="120" align="center">
          <template v-slot="scope">
            {{ formatStatus(scope.row.orderStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="결제 상태" width="120" align="center">
          <template v-slot="scope">
            {{ formatPayStatus(scope.row.orderStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="발송 상태" width="120" align="center">
          <template v-slot="scope">
            {{ formatDeliverStatus(scope.row.orderStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="메모" align="center">
          <template v-slot="scope">
            {{ scope.row.note }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog title="수령인 정보 수정" v-model="receiverDialogVisible" width="40%">
      <el-form :model="receiverInfo" ref="receiverInfoForm" label-width="150px">
        <el-form-item label="수령인 이름:">
          <el-input v-model="receiverInfo.receiverName" style="width: 200px"></el-input>
        </el-form-item>
        <el-form-item label="휴대폰 번호:">
          <el-input v-model="receiverInfo.receiverPhone" style="width: 200px">
          </el-input>
        </el-form-item>
        <el-form-item label="우편번호:">
          <el-input v-model="receiverInfo.receiverPostCode" style="width: 200px">
          </el-input>
        </el-form-item>
        <el-form-item label="소재 지역:">
          <el-cascader v-model="selectedRegions" :options="(pcaTextArr as CascaderOption[])"
            @change="onSelectRegionChange" placeholder="시/도, 시/군/구를 선택하세요"></el-cascader>
        </el-form-item>
        <el-form-item label="상세 주소:">
          <el-input v-model="receiverInfo.receiverDetailAddress" type="textarea" :rows="3">
          </el-input>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="receiverDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleUpdateReceiverInfo">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog title="비용 정보 수정" v-model="moneyDialogVisible" width="40%">
      <div class="table-layout">
        <el-row>
          <el-col :span="6" class="table-cell-title">상품 합계</el-col>
          <el-col :span="6" class="table-cell-title">배송비</el-col>
          <el-col :span="6" class="table-cell-title">쿠폰</el-col>
          <el-col :span="6" class="table-cell-title">포인트 차감</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell">₩{{ order.totalAmount }}</el-col>
          <el-col :span="6" class="table-cell">
            <el-input v-model.number="moneyInfo.freightAmount" size="small"><template
                v-slot:prepend>₩</template></el-input>
          </el-col>
          <el-col :span="6" class="table-cell">-₩{{ order.couponAmount }}</el-col>
          <el-col :span="6" class="table-cell">-₩{{ order.integrationAmount }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell-title">이벤트 할인</el-col>
          <el-col :span="6" class="table-cell-title">할인 금액</el-col>
          <el-col :span="6" class="table-cell-title">주문 총 금액</el-col>
          <el-col :span="6" class="table-cell-title">결제 예정 금액</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="table-cell">-₩{{ order.promotionAmount }}</el-col>
          <el-col :span="6" class="table-cell">
            <el-input v-model.number="moneyInfo.discountAmount" size="small"><template
                v-slot:prepend>-₩</template></el-input>
          </el-col>
          <el-col :span="6" class="table-cell">
            <span class="color-danger">₩{{ order.totalAmount + moneyInfo.freightAmount }}</span>
          </el-col>
          <el-col :span="6" class="table-cell">
            <span class="color-danger">₩{{ order.payAmount + moneyInfo.freightAmount - moneyInfo.discountAmount
            }}</span>
          </el-col>
        </el-row>
      </div>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="moneyDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleUpdateMoneyInfo">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog title="쪽지 전송" v-model="messageDialogVisible" width="40%">
      <el-form :model="message" ref="receiverInfoForm" label-width="150px">
        <el-form-item label="제목:">
          <el-input v-model="message.title" style="width: 200px"></el-input>
        </el-form-item>
        <el-form-item label="내용:">
          <el-input v-model="message.content" type="textarea" :rows="3">
          </el-input>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="messageDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleSendMessage">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog title="주문 종료" v-model:visible="closeDialogVisible" width="40%">
      <el-form :model="closeInfo" label-width="150px">
        <el-form-item label="작업 메모:">
          <el-input v-model="closeInfo.note" type="textarea" :rows="3">
          </el-input>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="closeDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleCloseOrder">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog title="주문 메모" v-model="markOrderDialogVisible" width="40%">
      <el-form :model="markInfo" label-width="150px">
        <el-form-item label="작업 메모:">
          <el-input v-model="markInfo.note" type="textarea" :rows="3">
          </el-input>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="markOrderDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleMarkOrder">확 인</el-button>
        </span>
      </template>
    </el-dialog>
    <logistics-dialog v-model="logisticsDialogVisible"></logistics-dialog>
  </div>
</template>

<style scoped>
.detail-container {
  width: 80%;
  padding: 20px 20px 20px 20px;
  margin: 20px auto;
}

.operate-container {
  background: #F2F6FC;
  height: 80px;
  margin: -20px -20px 0;
  line-height: 80px;
}

.operate-button-container {
  float: right;
  margin-right: 20px
}

.table-layout {
  margin-top: 20px;
  border-left: 1px solid #DCDFE6;
  border-top: 1px solid #DCDFE6;
}

.table-cell {
  height: 60px;
  line-height: 40px;
  border-right: 1px solid #DCDFE6;
  border-bottom: 1px solid #DCDFE6;
  padding: 10px;
  font-size: 14px;
  color: #606266;
  text-align: center;
  overflow: hidden;
}

.table-cell-title {
  border-right: 1px solid #DCDFE6;
  border-bottom: 1px solid #DCDFE6;
  padding: 10px;
  background: #F2F6FC;
  text-align: center;
  font-size: 14px;
  color: #303133;
}
</style>

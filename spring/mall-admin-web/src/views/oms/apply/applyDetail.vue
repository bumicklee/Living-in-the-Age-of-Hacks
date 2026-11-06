<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReturnApplyByIdAPI, returnApplyUpdateStatusAPI } from '@/apis/returnApply'
import { getCompanyAddressListAPI } from '@/apis/companyAddress'
import { formatDateTime } from '@/utils/datetime'
import type { OmsOrderReturnApply, OmsUpdateStatusParam } from '@/types/returnApply'
import type { OmsCompanyAddress } from '@/types/companyAddress'

// 기본 상태 수정 파라미터
const defaultUpdateStatusParam = {
  id: 0,
  companyAddressId: 0,
  handleMan: 'admin',
  handleNote: '',
  receiveMan: 'admin',
  receiveNote: '',
  returnAmount: 0,
  status: 0
}

// 라우트 관련
const route = useRoute()
const router = useRouter()

// 현재 반품 신청ID
const id = ref()
// 현재 반품 신청
const orderReturnApply = ref({} as OmsOrderReturnApply)
// 증빙 이미지
const proofPics = ref<string[]>([])
// 반품 상품 목록
const productList = ref()
// 회사 수령지 목록
const companyAddressList = ref<OmsCompanyAddress[]>([])
// 반품 신청 상태 수정 파라미터
const updateStatusParam = ref<OmsUpdateStatusParam>(Object.assign({}, defaultUpdateStatusParam))
// 상세 조회
const getDetail = async () => {
  const res = await getReturnApplyByIdAPI(id.value)
  orderReturnApply.value = res.data
  productList.value = []
  productList.value.push(orderReturnApply.value)
  if (orderReturnApply.value.proofPics) {
    proofPics.value = orderReturnApply.value.proofPics.split(",")
  }
  // 반품 중 및 완료
  if (orderReturnApply.value.status === 1 || orderReturnApply.value.status === 2) {
    updateStatusParam.value.returnAmount = orderReturnApply.value.returnAmount
    updateStatusParam.value.companyAddressId = orderReturnApply.value.companyAddressId
  }
}
// 회사 주소 목록 조회
const getCompanyAddressList = async () => {
  const res = await getCompanyAddressListAPI()
  companyAddressList.value = res.data
  // 기본 배송지 조회
  const defaultAddress = companyAddressList.value.find(item => item.receiveStatus === 1)
  if (defaultAddress) {
    updateStatusParam.value.companyAddressId = defaultAddress.id!
  }
}

// 컴포넌트 마운트
onMounted(() => {
  id.value = route.query.id
  getDetail()
  getCompanyAddressList()
})

// 계산된 속성
const totalAmount = computed(() => {
  if (orderReturnApply.value != null) {
    return orderReturnApply.value.productRealPrice * orderReturnApply.value.productCount
  } else {
    return 0
  }
})

// 현재 배송지
const currentAddress = computed(() => {
  const idValue = updateStatusParam.value.companyAddressId
  if (!companyAddressList.value) return undefined
  return companyAddressList.value.find(item => item.id === idValue)
})

// 상태 포맷
const formatStatus = (status: number) => {
  if (status === 0) {
    return "처리 대기"
  } else if (status === 1) {
    return "반품 중"
  } else if (status === 2) {
    return "완료"
  } else {
    return "거절됨"
  }
}

// 지역 포맷
const formatRegion = (address: OmsCompanyAddress | undefined) => {
  if (!address) return ''
  let str = address.province
  if (address.city) {
    str += "  " + address.city
  }
  str += "  " + address.region
  return str
}

// 주문 상세 보기
const handleViewOrder = () => {
  router.push({ path: '/oms/orderDetail', query: { id: orderReturnApply.value.orderId } })
}

// 상태 업데이트
const handleUpdateStatus = async (status: number) => {
  updateStatusParam.value.status = status
  await ElMessageBox.confirm('이 작업을 진행할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await returnApplyUpdateStatusAPI(id.value, updateStatusParam.value)
  ElMessage({
    type: 'success',
    message: '작업 성공!',
    duration: 1000
  })
  router.back()
}


</script>

<template>
  <div class="detail-container">
    <el-card shadow="never">
      <span class="font-title-medium">반품 상품</span>
      <el-table border class="standard-margin" ref="productTable" :data="productList">
        <el-table-column label="상품 이미지" width="160" align="center">
          <template #default="scope">
            <img style="height:80px" :src="scope.row.productPic">
          </template>
        </el-table-column>
        <el-table-column label="상품명" align="center">
          <template #default="scope">
            <span class="font-small">{{ scope.row.productName }}</span><br>
            <span class="font-small">브랜드:{{ scope.row.productBrand }}</span>
          </template>
        </el-table-column>
        <el-table-column label="가격/상품번호" width="180" align="center">
          <template #default="scope">
            <span class="font-small">가격: ₩{{ scope.row.productRealPrice }}</span><br>
            <span class="font-small">상품 번호:NO.{{ scope.row.productId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="속성" width="180" align="center">
          <template #default="scope">{{ scope.row.productAttr }}</template>
        </el-table-column>
        <el-table-column label="수량" width="100" align="center">
          <template #default="scope">{{ scope.row.productCount }}</template>
        </el-table-column>
        <el-table-column label="소계" width="100" align="center">
          <template>₩{{ totalAmount }}</template>
        </el-table-column>
      </el-table>
      <div style="float:right;margin-top:15px;margin-bottom:15px">
        <span class="font-title-medium">합계:</span>
        <span class="font-title-medium color-danger">₩{{ totalAmount }}</span>
      </div>
    </el-card>
    <el-card shadow="never" class="standard-margin">
      <span class="font-title-medium">서비스 요청 정보</span>
      <div class="form-container-border">
        <el-row>
          <el-col :span="6" class="form-border form-left-bg font-small">서비스 요청 번호</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.id }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">신청 상태</el-col>
          <el-col class="form-border font-small" :span="18">{{ formatStatus(orderReturnApply.status) }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="6" class="form-border form-left-bg font-small" style="height:50px;line-height:30px">주문 번호
          </el-col>
          <el-col class="form-border font-small" :span="18" style="height:50px">
            {{ orderReturnApply.orderSn }}
            <el-button type="text" size="small" @click="handleViewOrder">보기</el-button>
          </el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">신청 시간</el-col>
          <el-col class="form-border font-small" :span="18">{{ formatDateTime(orderReturnApply.createTime) }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">사용자 계정</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.memberUsername }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">연락처</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.returnName }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">연락처 전화번호</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.returnPhone }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">반품 사유</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.reason }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">문제 설명</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.description }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6" style="height:100px;line-height:80px">증빙 이미지
          </el-col>
          <el-col class="form-border font-small" :span="18" style="height:100px">
            <img v-for="item in proofPics" style="width:80px;height:80px" :src="item" :key="item">
          </el-col>
        </el-row>
      </div>
      <div class="form-container-border">
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">주문 금액</el-col>
          <el-col class="form-border font-small" :span="18">₩{{ totalAmount }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6" style="height:52px;line-height:32px">환불 금액 확인
          </el-col>
          <el-col class="form-border font-small" style="height:52px" :span="18">
            ₩
            <el-input size="small" v-model="updateStatusParam.returnAmount" :disabled="orderReturnApply.status !== 0"
              style="width:200px;margin-left: 10px"></el-input>
          </el-col>
        </el-row>
        <div v-show="orderReturnApply.status !== 3">
          <el-row>
            <el-col class="form-border form-left-bg font-small" :span="6" style="height:52px;line-height:32px">수령 장소 선택
            </el-col>
            <el-col class="form-border font-small" style="height:52px" :span="18">
              <el-select size="small" style="width:200px" :disabled="orderReturnApply.status !== 0"
                v-model="updateStatusParam.companyAddressId">
                <el-option v-for="address in companyAddressList" :key="address.id" :value="address.id!"
                  :label="address.addressName">
                </el-option>
              </el-select>
            </el-col>
          </el-row>
          <el-row>
            <el-col class="form-border form-left-bg font-small" :span="6">수령인 이름</el-col>
            <el-col class="form-border font-small" :span="18">{{ currentAddress?.name }}</el-col>
          </el-row>
          <el-row>
            <el-col class="form-border form-left-bg font-small" :span="6">소재 지역</el-col>
            <el-col class="form-border font-small" :span="18">{{ formatRegion(currentAddress)
              }}</el-col>
          </el-row>
          <el-row>
            <el-col class="form-border form-left-bg font-small" :span="6">상세 주소</el-col>
            <el-col class="form-border font-small" :span="18">{{ currentAddress?.detailAddress }}</el-col>
          </el-row>
          <el-row>
            <el-col class="form-border form-left-bg font-small" :span="6">연락처 전화번호</el-col>
            <el-col class="form-border font-small" :span="18">{{ currentAddress?.phone }}</el-col>
          </el-row>
        </div>
      </div>
      <div class="form-container-border" v-show="orderReturnApply.status !== 0">
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">처리 담당자</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.handleMan }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">처리 시간</el-col>
          <el-col class="form-border font-small" :span="18">{{ formatDateTime(orderReturnApply.handleTime) }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">메모 처리</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.handleNote }}</el-col>
        </el-row>
      </div>
      <div class="form-container-border" v-show="orderReturnApply.status === 2">
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">수령 담당자</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.receiveMan }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">수령 시간</el-col>
          <el-col class="form-border font-small" :span="18">{{ formatDateTime(orderReturnApply.receiveTime) }}</el-col>
        </el-row>
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6">배송 메모</el-col>
          <el-col class="form-border font-small" :span="18">{{ orderReturnApply.receiveNote }}</el-col>
        </el-row>
      </div>
      <div class="form-container-border" v-show="orderReturnApply.status === 0">
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6"
            style="height:52px;line-height:32px">메모 처리</el-col>
          <el-col class="form-border font-small" :span="18">
            <el-input size="small" v-model="updateStatusParam.handleNote"
              style="width:200px;margin-left: 10px"></el-input>
          </el-col>
        </el-row>
      </div>
      <div class="form-container-border" v-show="orderReturnApply.status === 1">
        <el-row>
          <el-col class="form-border form-left-bg font-small" :span="6"
            style="height:52px;line-height:32px">배송 메모</el-col>
          <el-col class="form-border font-small" :span="18">
            <el-input size="small" v-model="updateStatusParam.receiveNote"
              style="width:200px;margin-left: 10px"></el-input>
          </el-col>
        </el-row>
      </div>
      <div style="margin-top:15px;text-align: center" v-show="orderReturnApply.status === 0">
        <el-button type="primary" size="small" @click="handleUpdateStatus(1)">반품 확인</el-button>
        <el-button type="danger" size="small" @click="handleUpdateStatus(3)">반품 거절</el-button>
      </div>
      <div style="margin-top:15px;text-align: center" v-show="orderReturnApply.status === 1">
        <el-button type="primary" size="small" @click="handleUpdateStatus(2)">수취 확인</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.detail-container {
  position: absolute;
  left: 0;
  right: 0;
  width: 1080px;
  padding: 35px 35px 15px 35px;
  margin: 20px auto;
}

.standard-margin {
  margin-top: 15px;
}

.form-border {
  border-right: 1px solid #DCDFE6;
  border-bottom: 1px solid #DCDFE6;
  padding: 10px;
}

.form-container-border {
  border-left: 1px solid #DCDFE6;
  border-top: 1px solid #DCDFE6;
  margin-top: 15px;
}

.form-left-bg {
  background: #F2F6FC;
}
</style>

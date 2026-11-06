<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { formatDate, formatDateTime } from '@/utils/datetime'
import { Search } from '@element-plus/icons-vue'
import { getCouponByIdAPI, getCouponHistoryListAPI } from '@/apis/coupon'
import type { CouponHistoryQueryParam, SmsCoupon, SmsCouponHistory } from '@/types/coupon'
import { couponTypes } from '@/utils/constant'
import { useRoute } from 'vue-router'

// 라우트 조회
const route = useRoute()
// 쿠폰 상세
const coupon = ref<SmsCoupon>({} as SmsCoupon)
// 쿠폰 내역 목록 조회 파라미터
const listQuery = ref<CouponHistoryQueryParam>({
  pageNum: 1,
  pageSize: 10,
})
// 쿠폰 내역 목록 데이터
const list = ref<SmsCouponHistory[]>([])
// 총 레코드 수
const total = ref(0)
// 테이블 데이터 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getCouponHistoryListAPI(listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error('쿠폰 내역 목록 조회 실패:', error)
  }
}

// 사용 상태 옵션
const useTypeOptions = ref([
  { label: "미사용", value: 0 },
  { label: "사용 완료", value: 1 },
  { label: "만료됨", value: 2 }
])

// 페이지 로딩 완료 후 데이터 조회
onMounted(async () => {
  // 쿠폰 상세 조회
  const couponRes = await getCouponByIdAPI(Number(route.query.id))
  coupon.value = couponRes.data
  // 이력 조회용 쿠폰 ID 설정
  listQuery.value.couponId = Number(route.query.id)
  getList()
})

// 검색 조건 초기화
const handleResetSearch = () => {
  listQuery.value = {
    pageNum: 1,
    pageSize: 10,
    couponId: Number(route.query.id)
  }
}

// 검색 목록
const handleSearchList = async () => {
  listQuery.value.pageNum = 1
  await getList()
}

// 페이지당 건수 변경
const handleSizeChange = (val: number) => {
  listQuery.value.pageNum = 1
  listQuery.value.pageSize = val
  getList()
}

// 현재 페이지 변경
const handleCurrentChange = (val: number) => {
  listQuery.value.pageNum = val
  getList()
}

// 쿠폰 유형 필터
const formatType = (type: number) => {
  const found = couponTypes.find(option => option.value === type)
  return found ? found.label : ''
}

// 쿠폰 사용 유형 필터
const formatUseType = (useType: number) => {
  if (useType === 0) {
    return '전 상품 공용'
  } else if (useType === 1) {
    return '지정 카테고리'
  } else {
    return '지정 상품'
  }
}

// 쿠폰 만료 상태 필터
const formatStatus = (endTime: string | number | Date) => {
  if (!endTime) return ''
  const endTimeDate = new Date(endTime).getTime()
  const now = new Date().getTime()
  return endTimeDate > now ? '미만료' : '만료됨'
}

// 타입 필터 조회
const formatGetType = (type: number) => {
  if (type === 1) {
    return '직접 조회'
  } else {
    return '관리자 지급'
  }
}

// 쿠폰 내역 사용 유형 필터
const formatCouponHistoryUseType = (useType: number) => {
  if (useType === 0) {
    return '미사용'
  } else if (useType === 1) {
    return '사용 완료'
  } else {
    return '만료됨'
  }
}
</script>

<template>
  <div class="app-container">
    <div class="table-layout">
      <el-row>
        <el-col :span="4" class="table-cell-title">이름</el-col>
        <el-col :span="4" class="table-cell-title">쿠폰 유형</el-col>
        <el-col :span="4" class="table-cell-title">사용 가능 상품</el-col>
        <el-col :span="4" class="table-cell-title">사용 조건</el-col>
        <el-col :span="4" class="table-cell-title">액면가</el-col>
        <el-col :span="4" class="table-cell-title">상태</el-col>
      </el-row>
      <el-row>
        <el-col :span="4" class="table-cell">{{ coupon.name }}</el-col>
        <el-col :span="4" class="table-cell">{{ formatType(coupon.type) }}</el-col>
        <el-col :span="4" class="table-cell">{{ formatUseType(coupon.useType) }}</el-col>
        <el-col :span="4" class="table-cell">이상{{ coupon.minPoint }}원 사용 가능</el-col>
        <el-col :span="4" class="table-cell">{{ coupon.amount }}원</el-col>
        <el-col :span="4" class="table-cell">{{ formatStatus(coupon.endTime!) }}</el-col>
      </el-row>
      <el-row>
        <el-col :span="4" class="table-cell-title">유효 기간</el-col>
        <el-col :span="4" class="table-cell-title">총 발행량</el-col>
        <el-col :span="4" class="table-cell-title">수령 완료</el-col>
        <el-col :span="4" class="table-cell-title">수령 대기</el-col>
        <el-col :span="4" class="table-cell-title">사용 완료</el-col>
        <el-col :span="4" class="table-cell-title">미사용</el-col>
      </el-row>
      <el-row>
        <el-col :span="4" class="table-cell" style="font-size: 13px">
          {{ formatDate(coupon.startTime!) }}~{{ formatDate(coupon.endTime!) }}
        </el-col>
        <el-col :span="4" class="table-cell">{{ coupon.publishCount }}</el-col>
        <el-col :span="4" class="table-cell">{{ coupon.receiveCount }}</el-col>
        <el-col :span="4" class="table-cell">{{ coupon.publishCount! - coupon.receiveCount! }}</el-col>
        <el-col :span="4" class="table-cell">{{ coupon.useCount }}</el-col>
        <el-col :span="4" class="table-cell">{{ coupon.publishCount! - coupon.useCount! }}</el-col>
      </el-row>
    </div>
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
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="사용 상태:">
            <el-select v-model="listQuery.useStatus" placeholder="전체" clearable style="width: 150px;">
              <el-option v-for="item in useTypeOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="주문 번호:">
            <el-input v-model="listQuery.orderSn" class="input-width" placeholder="주문 번호"></el-input>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <div class="table-container">
      <el-table ref="couponHistoryTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="쿠폰 코드" width="160" align="center">
          <template #default="scope">{{ scope.row.couponCode }}</template>
        </el-table-column>
        <el-table-column label="수령 회원" width="140" align="center">
          <template #default="scope">{{ scope.row.memberNickname }}</template>
        </el-table-column>
        <el-table-column label="수령 방식" width="100" align="center">
          <template #default="scope">{{ formatGetType(scope.row.getType) }}</template>
        </el-table-column>
        <el-table-column label="수령 시간" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="현재 상태" width="140" align="center">
          <template #default="scope">{{ formatCouponHistoryUseType(scope.row.useStatus) }}</template>
        </el-table-column>
        <el-table-column label="사용 시간" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.useTime) }}</template>
        </el-table-column>
        <el-table-column label="주문 번호" align="center">
          <template #default="scope">{{ scope.row.orderSn === null ? 'N/A' : scope.row.orderSn }}</template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" v-model:current-page="listQuery.pageNum"
        :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="total">
      </el-pagination>
    </div>
  </div>
</template>

<style scoped>
.app-container {
  width: 80%;
  margin: 20px auto;
}

.filter-container {
  margin-top: 20px;
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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCouponListAPI, couponDeleteByIdAPI } from '@/apis/coupon'
import { formatDate } from '@/utils/datetime'
import type { CouponQueryParam, SmsCoupon } from '@/types/coupon'
import { couponPlatforms, couponTypes } from '@/utils/constant'

// 라우트 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<CouponQueryParam>({ pageNum: 1, pageSize: 10 })
// 유형 옵션
const typeOptions = ref(couponTypes)
// 목록 데이터
const list = ref<SmsCoupon[]>([])
// 총 개수
const total = ref<number>(0)
// 로딩 상태
const listLoading = ref<boolean>(false)
// 다중 선택 데이터
const multipleSelection = ref<SmsCoupon[]>([])

// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getCouponListAPI(listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error('쿠폰 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
})

// 검색 조건 초기화
const handleResetSearch = () => {
  listQuery.value = { pageNum: 1, pageSize: 10 }
}

// 검색 목록
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 다중 선택 변경 처리
const handleSelectionChange = (val: SmsCoupon[]) => {
  multipleSelection.value = val
}

// 페이지 크기 변경 처리
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

// 쿠폰 추가
const handleAdd = () => {
  router.push({ path: '/sms/addCoupon' })
}

// 쿠폰 보기
const handleView = (index: number, row: SmsCoupon) => {
  router.push({ path: '/sms/couponHistory', query: { id: row.id } })
}

// 쿠폰 업데이트
const handleUpdate = (index: number, row: SmsCoupon) => {
  router.push({ path: '/sms/updateCoupon', query: { id: row.id } })
}

// 쿠폰 삭제
const handleDelete = async (index: number, row: SmsCoupon) => {
  try {
    await ElMessageBox.confirm('삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await couponDeleteByIdAPI(row.id!)
    ElMessage({
      type: 'success',
      message: '삭제 성공!'
    })
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('쿠폰 삭제 실패:', error)
    }
  }
}

// 유형 포맷
const formatType = (type: number) => {
  const findItem = typeOptions.value.find(item => item.value === type)
  return findItem?.label
}

// 사용 유형 포맷
const formatUseType = (useType: number) => {
  if (useType === 0) {
    return '전 상품 공용'
  } else if (useType === 1) {
    return '지정 카테고리'
  } else {
    return '지정 상품'
  }
}

// 플랫폼 포맷
const formatPlatform = (platform: number) => {
  const findItem = couponPlatforms.find(item => item.value === platform)
  return findItem?.label
}

// 상태 포맷
const formatStatus = (endTime: string) => {
  const now = new Date().getTime()
  const endDate = new Date(endTime)
  if (endDate.getTime() > now) {
    return '미만료'
  } else {
    return '만료됨'
  }
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
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="쿠폰 이름:">
            <el-input v-model="listQuery.name" class="input-width" placeholder="쿠폰 이름"></el-input>
          </el-form-item>
          <el-form-item label="쿠폰 유형:">
            <el-select v-model="listQuery.type" placeholder="전체" clearable class="input-width">
              <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value">
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
      <el-button class="btn-add" @click="handleAdd()">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="couponTable" :data="list" style="width: 100%;" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="쿠폰 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="쿠폰 유형" width="100" align="center">
          <template #default="scope">{{ formatType(scope.row.type) }}</template>
        </el-table-column>
        <el-table-column label="사용 가능 상품" width="100" align="center">
          <template #default="scope">{{ formatUseType(scope.row.useType) }}</template>
        </el-table-column>
        <el-table-column label="사용 조건" width="140" align="center">
          <template #default="scope">이상{{ scope.row.minPoint }}원 사용 가능</template>
        </el-table-column>
        <el-table-column label="액면가" width="100" align="center">
          <template #default="scope">{{ scope.row.amount }}원</template>
        </el-table-column>
        <el-table-column label="적용 플랫폼" width="100" align="center">
          <template #default="scope">{{ formatPlatform(scope.row.platform) }}</template>
        </el-table-column>
        <el-table-column label="유효 기간" width="180" align="center">
          <template #default="scope">{{ formatDate(scope.row.startTime) }}~{{ formatDate(scope.row.endTime)
          }}</template>
        </el-table-column>
        <el-table-column label="상태" width="100" align="center">
          <template #default="scope">{{ formatStatus(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleView(scope.$index, scope.row)">보기</el-button>
            <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">
              편집</el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제</el-button>
          </template>
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
.input-width {
  width: 203px;
}
</style>

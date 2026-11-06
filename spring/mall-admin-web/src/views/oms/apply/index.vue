<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/datetime'
import { getReturnApplyListAPI, returnApplyDeleteByIdsAPI } from '@/apis/returnApply'
import type { OmsOrderReturnApply, ReturnApplyQueryParam } from '@/types/returnApply'

// 라우트 객체 조회
const router = useRouter()

// 기본 처리 상태
const defaultStatusOptions = [
  {
    label: '처리 대기',
    value: 0
  },
  {
    label: '반품 중',
    value: 1
  },
  {
    label: '완료',
    value: 2
  },
  {
    label: '거절됨',
    value: 3
  }
]

// 목록 조회 파라미터
const listQuery = ref<ReturnApplyQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 상태 옵션
const statusOptions = ref(Object.assign({}, defaultStatusOptions))
// 목록 데이터
const list = ref<OmsOrderReturnApply[]>([])
// 총 개수
const total = ref(0)
// 로딩 상태
const listLoading = ref(false)
// 다중 선택 데이터
const multipleSelection = ref<OmsOrderReturnApply[]>([])
// 목록 조회
const getList = async () => {
  listLoading.value = true
  const res = await getReturnApplyListAPI(listQuery.value)
  listLoading.value = false
  list.value = res.data.list
  total.value = res.data.total
}

// 컴포넌트 마운트 후 목록 조회
onMounted(() => {
  getList()
})

// 작업 유형
const operateType = ref()
// 작업 옵션
const operateOptions = ref([
  {
    label: "일괄 삭제",
    value: 1
  }
])

// 상태 포맷
const formatStatus = (status: number) => {
  return defaultStatusOptions.find(item => item.value === status)?.label
}

// 환불 금액 포맷
const formatReturnAmount = (row: OmsOrderReturnApply) => {
  return row.productRealPrice * row.productCount
}

// 선택 변경 처리
const handleSelectionChange = (val: OmsOrderReturnApply[]) => {
  multipleSelection.value = val
}

// 검색 초기화
const handleResetSearch = () => {
  listQuery.value = { pageNum: 1, pageSize: 10 }
}

// 검색 목록
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 상세 보기
const handleViewDetail = (index: number, row: OmsOrderReturnApply) => {
  router.push({ path: '/oms/returnApplyDetail', query: { id: row.id } })
}

// 일괄 작업
const handleBatchOperate = async () => {
  if (!multipleSelection.value || multipleSelection.value.length < 1) {
    ElMessage({
      message: '작업할 신청을 선택하세요',
      type: 'warning',
      duration: 1000
    })
    return
  }
  if (operateType.value === 1) {
    // 일괄 삭제
    await ElMessageBox.confirm('삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await returnApplyDeleteByIdsAPI({ ids: multipleSelection.value.map(item => item.id).join(',') })
    getList()
    ElMessage({
      type: 'success',
      message: '삭제 성공!'
    })
  }
}

// 페이지당 크기 변경 처리
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
            <el-input v-model="listQuery.id" class="input-width" placeholder="서비스 요청 번호"></el-input>
          </el-form-item>
          <el-form-item label="처리 상태:">
            <el-select v-model="listQuery.status" placeholder="전체" clearable class="input-width">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="신청 시간:">
            <el-date-picker class="input-width" v-model="listQuery.createTime" value-format="yyyy-MM-dd" type="date"
              placeholder="시간을 선택하세요">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="작업 담당자:">
            <el-input v-model="listQuery.handleMan" class="input-width" placeholder="전체"></el-input>
          </el-form-item>
          <el-form-item label="처리 시간:">
            <el-date-picker class="input-width" v-model="listQuery.handleTime" value-format="yyyy-MM-dd" type="date"
              placeholder="시간을 선택하세요">
            </el-date-picker>
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
      <el-table ref="returnApplyTable" :data="list" style="width: 100%;" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="서비스 요청 번호" width="180" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="신청 시간" width="180" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="사용자 계정" align="center">
          <template #default="scope">{{ scope.row.memberUsername }}</template>
        </el-table-column>
        <el-table-column label="환불 금액" width="180" align="center">
          <template #default="scope">₩{{ formatReturnAmount(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="신청 상태" width="180" align="center">
          <template #default="scope">{{ formatStatus(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="처리 시간" width="180" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.handleTime) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleViewDetail(scope.$index, scope.row)">상세 보기</el-button>
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
  </div>
</template>

<style scoped>
.input-width {
  width: 203px
}
</style>

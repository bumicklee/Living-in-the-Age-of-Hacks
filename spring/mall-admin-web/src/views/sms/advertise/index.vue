<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getHomeAdvertiseListAPI, homeAdvertiseUpdateStatusAPI, deleteHomeAdvertiseAPI } from '@/apis/homeAdvertise'
import { formatDateTime } from '@/utils/datetime'
import type { HomeAdvertiseQueryParam, SmsHomeAdvertise } from '@/types/homeAdvertist'

// 라우트 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<HomeAdvertiseQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 배너 위치 옵션
const typeOptions = ref([
  {
    label: 'PC홈 배너',
    value: 0
  },
  {
    label: 'APP홈 배너',
    value: 1
  }
])
// 목록 데이터
const list = ref<SmsHomeAdvertise[]>([])
// 총 건수
const total = ref(0)
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  const res = await getHomeAdvertiseListAPI(listQuery.value)
  listLoading.value = false
  list.value = res.data.list
  total.value = res.data.total
}

// 컴포넌트 마운트 후 실행
onMounted(() => {
  getList()
})

// 선택된 항목
const multipleSelection = ref<SmsHomeAdvertise[]>([])
// 일괄 작업 유형
const operates = ref([
  {
    label: "삭제",
    value: 0
  }
])
// 일정 일괄 작업 유형
const operateType = ref()

// 검색 초기화
const handleResetSearch = () => {
  listQuery.value = { pageNum: 1, pageSize: 10 }
}

// 검색 목록
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 테이블 선택 변경 처리
const handleSelectionChange = (val: SmsHomeAdvertise[]) => {
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

// 상태 갱신 처리
const handleUpdateStatus = async (index: number, row: SmsHomeAdvertise) => {
  try {
    await ElMessageBox.confirm('판매 시작/중지 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await homeAdvertiseUpdateStatusAPI({ id: row.id!, status: row.status! })
    getList()
    ElMessage.success({
      type: 'success',
      message: '수정 성공!'
    })
  } catch {
    getList()
  }
}

// 광고 삭제
const deleteHomeAdvertise = async (ids: number[]) => {
  await ElMessageBox.confirm('이 광고를 삭제할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await deleteHomeAdvertiseAPI({ ids: ids.join(',') })
  getList()
  ElMessage.success({
    type: 'success',
    message: '삭제 성공!'
  })
}

// 삭제 처리
const handleDelete = async (index: number, row: SmsHomeAdvertise) => {
  await deleteHomeAdvertise([row.id!])
}

// 일괄 작업 처리
const handleBatchOperate = async () => {
  if (multipleSelection.value.length < 1) {
    ElMessage.warning({
      message: '레코드를 하나 선택하세요',
      duration: 1000
    })
    return
  }
  if (operateType.value === 0) {
    //삭제
    await deleteHomeAdvertise(multipleSelection.value.map(item => item.id!))
  } else {
    ElMessage.warning({
      message: '일괄 작업 유형을 선택하세요',
      duration: 1000
    })
  }
}

// 추가 처리
const handleAdd = () => {
  router.push({ path: '/sms/addAdvertise' })
}

// 갱신 처리
const handleUpdate = (index: number, row: SmsHomeAdvertise) => {
  router.push({ path: '/sms/updateAdvertise', query: { id: row.id } })
}

// 유형 포맷
const formatType = (type: number) => {
  if (type === 1) {
    return 'APP홈 배너'
  } else {
    return 'PC홈 배너'
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
      <div style="margin-top: 25px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="광고명:">
            <el-input v-model="listQuery.name" class="input-width" placeholder="광고명"></el-input>
          </el-form-item>
          <el-form-item label="광고 위치:">
            <el-select v-model="listQuery.type" placeholder="전체" clearable class="input-width">
              <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="만료 시간:">
            <el-date-picker class="input-width" v-model="listQuery.endTime" value-format="yyyy-MM-dd" type="date"
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
      <el-button class="btn-add" @click="handleAdd()">광고 추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="homeAdvertiseTable" :data="list" style="width: 100%" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="광고명" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="광고 위치" width="120" align="center">
          <template #default="scope">{{ formatType(scope.row.type) }}</template>
        </el-table-column>
        <el-table-column label="광고 이미지" width="180" align="center">
          <template #default="scope"><img style="height: 80px;width: 150px;" :src="scope.row.pic"></template>
        </el-table-column>
        <el-table-column label="시간" width="240" align="center">
          <template #default="scope">
            <p>시작 시간:{{ formatDateTime(scope.row.startTime) }}</p>
            <p>만료 시간:{{ formatDateTime(scope.row.endTime) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="온라인/오프라인" width="100" align="center">
          <template #default="scope">
            <el-switch @change="handleUpdateStatus(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.status">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="클릭 횟수" width="100" align="center">
          <template #default="scope">{{ scope.row.clickCount }}</template>
        </el-table-column>
        <el-table-column label="주문 생성" width="100" align="center">
          <template #default="scope">{{ scope.row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="작업" width="120" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">편집
            </el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="batch-operate-container">
      <el-select v-model="operateType" placeholder="일괄 작업">
        <el-option v-for="item in operates" :key="item.value" :label="item.label" :value="item.value">
        </el-option>
      </el-select>
      <el-button style="margin-left: 20px" class="search-button" @click="handleBatchOperate()" type="primary">
        확인
      </el-button>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]"
        v-model:current-page="listQuery.pageNum" :total="total">
      </el-pagination>
    </div>
  </div>
</template>

<style scoped>
.input-width {
  width: 203px;
}
</style>

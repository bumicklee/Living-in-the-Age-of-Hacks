<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { getReturnReasonListAPI, returnReasonDeleteByIdsAPI, returnReasonUpdateStatusAPI, returnReasonCreateAPI, getReturnReasonByIdAPI, returnReasonUpdateAPI } from '@/apis/returnReason'
import { formatDateTime } from '@/utils/datetime'
import type { PageParam } from '@/types/common'
import type { OmsOrderReturnReason } from '@/types/returnReason'

// 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10
})
// 목록 데이터
const list = ref<OmsOrderReturnReason[]>([])
// 목록 전체 건수
const total = ref(0)
// 테이블에서 선택된 행
const multipleSelection = ref<OmsOrderReturnReason[]>([])
// 테이블 데이터 로딩 진행 표시줄
const listLoading = ref(true)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  const res = await getReturnReasonListAPI(listQuery.value)
  listLoading.value = false
  list.value = res.data.list
  total.value = res.data.total
}

// 컴포넌트 마운트 후 데이터 로드
onMounted(() => {
  getList()
})

// reason객체 기본값
const defaultReturnReason: OmsOrderReturnReason = {
  name: '',
  sort: 0,
  status: 1,
}
// 편집창 표시 여부
const dialogVisible = ref(false)
// 현재 작업 중인 reason 객체
const returnReason = ref(Object.assign({}, defaultReturnReason))
// 현재 작업 중인 reasonId, null이면 신규 추가
const operateReasonId = ref<number>()

// 일괄 작업 유형
const operateType = ref()
// 전체 일괄 작업
const operateOptions = ref([
  {
    label: "삭제",
    value: 1
  }
])

const handleAdd = () => {
  dialogVisible.value = true
  operateReasonId.value = undefined
  returnReason.value = Object.assign({}, defaultReturnReason)
}

const handleConfirm = async () => {
  if (!operateReasonId.value) {
    // 추가 작업
    await returnReasonCreateAPI(returnReason.value)
    dialogVisible.value = false
    operateReasonId.value = undefined
    ElMessage({
      message: '추가 성공!',
      type: 'success',
      duration: 1000
    })
    getList()
  } else {
    // 편집 작업
    await returnReasonUpdateAPI(operateReasonId.value, returnReason.value)
    dialogVisible.value = false
    operateReasonId.value = undefined
    ElMessage({
      message: '수정 성공!',
      type: 'success',
      duration: 1000
    })
    getList()
  }
}

const handleUpdate = async (index: number, row: OmsOrderReturnReason) => {
  dialogVisible.value = true
  operateReasonId.value = row.id
  const res = await getReturnReasonByIdAPI(row.id!)
  returnReason.value = res.data
}

const handleDelete = (index: number, row: OmsOrderReturnReason) => {
  const ids = []
  ids.push(row.id!)
  deleteReasonMethod(ids)
}

const handleSelectionChange = (val: OmsOrderReturnReason[]) => {
  multipleSelection.value = val
}

const handleStatusChange = async (index: number, row: OmsOrderReturnReason) => {
  const ids = []
  ids.push(row.id)
  await returnReasonUpdateStatusAPI({ ids: ids.join(','), status: row.status })
  ElMessage({
    message: '상태 수정 성공',
    type: 'success'
  })
}

const handleBatchOperate = () => {
  if (!multipleSelection.value || multipleSelection.value.length < 1) {
    ElMessage({
      message: '작업할 항목을 선택하세요',
      type: 'warning',
      duration: 1000
    })
    return
  }
  if (operateType.value === 1) {
    deleteReasonMethod(multipleSelection.value.map(item => item.id!))
  }
}

const handleSizeChange = (val: number) => {
  listQuery.value.pageNum = 1
  listQuery.value.pageSize = val
  getList()
}

const handleCurrentChange = (val: number) => {
  listQuery.value.pageNum = val
  getList()
}

const deleteReasonMethod = async (ids: number[]) => {
  await ElMessageBox.confirm('삭제할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await returnReasonDeleteByIdsAPI({ ids: ids.join(',') })
  ElMessage({
    message: '삭제 성공!',
    type: 'success',
    duration: 1000
  })
  listQuery.value.pageNum = 1
  getList()
}
</script>

<template>
  <div class="app-container">
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button @click="handleAdd" class="btn-add">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="returnReasonTable" :data="list" style="width: 100%;" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="80" align="center">
          <template v-slot="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="사유 유형" align="center">
          <template v-slot="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template v-slot="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="사용 가능 여부" align="center">
          <template v-slot="scope">
            <el-switch v-model="scope.row.status" @change="handleStatusChange(scope.$index, scope.row)"
              :active-value="1" :inactive-value="0">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="추가 시간" width="180" align="center">
          <template v-slot="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="160" align="center">
          <template v-slot="scope">
            <el-button size="small" @click="handleUpdate(scope.$index, scope.row)">편집</el-button>
            <el-button size="small" @click="handleDelete(scope.$index, scope.row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="batch-operate-container">
      <el-select v-model="operateType" placeholder="일괄 작업">
        <el-option v-for="item in operateOptions" :key="item.value" :label="item.label" :value="item.value">
        </el-option>
      </el-select>
      <el-button style="margin-left: 20px" class="search-button" @click="handleBatchOperate" type="primary">
        확인
      </el-button>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" v-model:current-page="listQuery.pageNum"
        :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="total">
      </el-pagination>
    </div>
    <el-dialog title="반품 사유 추가" v-model="dialogVisible" width="30%">
      <el-form :model="returnReason" ref="reasonForm" label-width="150px">
        <el-form-item label="사유 유형:">
          <el-input v-model="returnReason.name" class="input-width"></el-input>
        </el-form-item>
        <el-form-item label="정렬:">
          <el-input v-model="returnReason.sort" class="input-width"></el-input>
        </el-form-item>
        <el-form-item label="사용 여부:">
          <el-switch v-model="returnReason.status" :active-value="1" :inactive-value="0"></el-switch>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleConfirm">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.input-width {
  width: 80%
}
</style>

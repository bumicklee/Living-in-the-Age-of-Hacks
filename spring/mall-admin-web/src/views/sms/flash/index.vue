<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getFlashListAPI, flashUpdateStatusByIdAPI, flashDeleteByIdAPI, flashCreateAPI, flashUpdateByIdAPI } from '@/apis/flash'
import { formatDate } from '@/utils/datetime'
import type { SmsFlashPromotion } from '@/types/flash'
import type { PageParam } from '@/types/common'

// 라우트 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})
// 목록 데이터
const list = ref<SmsFlashPromotion[]>([])
// 총 건수
const total = ref<number>(0)
// 이미지 로드
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getFlashListAPI(listQuery.value)
    listLoading.value = false
    list.value = res.data.list
    total.value = res.data.total
  } catch (error) {
    listLoading.value = false
    console.error('타임 세일 행사 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
})

// 기본 타임 세일 행사 객체
const defaultSmsFlashPromotion: SmsFlashPromotion = {
  title: '',
  status: 0
}
// 현재 작업 중인 타임 세일 이벤트 객체
const flashPromotion = ref(Object.assign({}, defaultSmsFlashPromotion))
// 편집창 표시 여부
const dialogVisible = ref(false)
// 편집 상태 여부
const isEdit = ref(false)

// 검색 조건 초기화
const handleResetSearch = () => {
  listQuery.value = { pageNum: 0, pageSize: 10 }
}

// 검색 목록
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 페이지 크기 변경
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

// 이벤트 추가
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  flashPromotion.value = Object.assign({}, defaultSmsFlashPromotion)
}

// 시간대 목록 표시
const handleShowSessionList = () => {
  router.push({ path: '/sms/flashSession' })
}

// 상태 변경
const handleStatusChange = async (index: number, row: SmsFlashPromotion) => {
  try {
    await ElMessageBox.confirm('이 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashUpdateStatusByIdAPI(row.id!, { status: row.status })
    ElMessage.success('수정 성공!')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('상태 업데이트 실패:', error)
    }
    ElMessage.info('수정 취소')
    getList()
  }
}

// 이벤트 삭제
const handleDelete = async (index: number, row: SmsFlashPromotion) => {
  try {
    await ElMessageBox.confirm('이 이벤트를 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('이벤트 삭제 실패:', error)
    }
  }
}

// 이벤트 업데이트
const handleUpdate = (index: number, row: SmsFlashPromotion) => {
  dialogVisible.value = true
  isEdit.value = true
  flashPromotion.value = Object.assign({}, row)
}

// 대화 상자 확인 처리
const handleDialogConfirm = async () => {
  await ElMessageBox.confirm('확인할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  try {
    if (isEdit.value) {
      await flashUpdateByIdAPI(flashPromotion.value.id!, flashPromotion.value)
      ElMessage.success('수정 성공!')
      dialogVisible.value = false
      getList()
    } else {
      await flashCreateAPI(flashPromotion.value)
      ElMessage.success('추가 성공!')
      dialogVisible.value = false
      getList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('행사 처리 실패:', error)
    }
  }
}

// 시간대 선택
const handleSelectSession = (index: number, row: SmsFlashPromotion) => {
  router.push({ path: '/sms/selectSession', query: { flashPromotionId: row.id } })
}

// 이벤트 상태 포맷
const formatActiveStatus = (row: SmsFlashPromotion) => {
  const nowDate = new Date()
  const startDate = new Date(row.startDate!)
  const endDate = new Date(row.endDate!)
  if (nowDate.getTime() >= startDate.getTime() && nowDate.getTime() <= endDate.getTime()) {
    return '이벤트 진행 중'
  } else if (nowDate.getTime() > endDate.getTime()) {
    return '이벤트 종료'
  } else {
    return '이벤트 시작 전'
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
          <el-form-item label="이벤트 이름:">
            <el-input v-model="listQuery.keyword" class="input-width" placeholder="이벤트 이름" clearable></el-input>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAdd()" style="margin-left: 20px">이벤트 추가</el-button>
      <el-button class="btn-add" @click="handleShowSessionList()">타임 세일 시간대 목록</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="flashTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="이벤트 제목" align="center">
          <template #default="scope">{{ scope.row.title }}</template>
        </el-table-column>
        <el-table-column label="이벤트 상태" width="140" align="center">
          <template #default="scope">{{ formatActiveStatus(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="시작 시간" width="140" align="center">
          <template #default="scope">{{ formatDate(scope.row.startDate) }}</template>
        </el-table-column>
        <el-table-column label="종료 시간" width="140" align="center">
          <template #default="scope">{{ formatDate(scope.row.endDate) }}</template>
        </el-table-column>
        <el-table-column label="온라인/오프라인" width="200" align="center">
          <template #default="scope">
            <el-switch @change="handleStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.status">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleSelectSession(scope.$index, scope.row)">상품 설정
            </el-button>
            <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">
              편집
            </el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
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
    <el-dialog title="이벤트 추가" v-model="dialogVisible" width="40%">
      <el-form :model="flashPromotion" ref="SmsFlashPromotionForm" label-width="150px">
        <el-form-item label="이벤트 제목:">
          <el-input v-model="flashPromotion.title" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="시작 시간:">
          <el-date-picker v-model="flashPromotion.startDate" type="date" placeholder="시간을 선택하세요">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="종료 시간:">
          <el-date-picker v-model="flashPromotion.endDate" type="date" placeholder="시간을 선택하세요">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="온라인/오프라인">
          <el-radio-group v-model="flashPromotion.status">
            <el-radio :label="1">온라인</el-radio>
            <el-radio :label="0">오프라인</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleDialogConfirm()">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style></style>

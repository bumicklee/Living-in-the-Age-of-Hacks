<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { getFlashSessionListAPI, flashSessionUpdateStatusByIdAPI, flashSessionDeleteByIdAPI, flashSessionCreateAPI, flashSessionUpdateByIdAPI } from '@/apis/flashSession'
import type { SmsFlashPromotionSession } from '@/types/flash'
import { formatTime } from '@/utils/datetime'

// 타임 세일 시간대 목록 데이터
const list = ref<SmsFlashPromotionSession[]>([])
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getFlashSessionListAPI()
    listLoading.value = false
    list.value = res.data
  } catch (error) {
    listLoading.value = false
    console.error('타임 세일 시간대 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
})

// 기본 타임 세일 회차 객체
const defaultFlashSession: SmsFlashPromotionSession = {
  name: '',
  status: 0
}
// 현재 작업 중인 타임 세일 회차
const flashSession = ref(Object.assign({}, defaultFlashSession))
// 편집창 표시 상태
const dialogVisible = ref(false)
// 편집 모드 여부
const isEdit = ref(false)

// 시간대 추가
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  flashSession.value = Object.assign({}, defaultFlashSession)
}

// 상태 변경
const handleStatusChange = async (index: number, row: SmsFlashPromotionSession) => {
  try {
    await ElMessageBox.confirm('이 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashSessionUpdateStatusByIdAPI(row.id!, { status: row.status })
    ElMessage.success('수정 성공!')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('상태 업데이트 실패:', error)
    }
    ElMessage.info('수정 취소')
    getList()
  }
}

// 시간대 업데이트
const handleUpdate = (index: number, row: SmsFlashPromotionSession) => {
  dialogVisible.value = true
  isEdit.value = true
  flashSession.value = Object.assign({}, row)
  if (row.startTime) {
    flashSession.value.startTime = row.startTime
  }
  if (row.endTime) {
    flashSession.value.endTime = row.endTime
  }
}

// 시간대 삭제
const handleDelete = async (index: number, row: SmsFlashPromotionSession) => {
  try {
    await ElMessageBox.confirm('이 시간대를 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashSessionDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('시간대 삭제 실패:', error)
    }
  }
}

// 대화 상자 확인 처리
const handleDialogConfirm = async () => {
  try {
    await ElMessageBox.confirm('확인할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (isEdit.value) {
      await flashSessionUpdateByIdAPI(flashSession.value.id!, flashSession.value)
      ElMessage.success('수정 성공!')
      dialogVisible.value = false
      getList()
    } else {
      await flashSessionCreateAPI(flashSession.value)
      ElMessage.success('추가 성공!')
      dialogVisible.value = false
      getList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('시간대 처리 실패:', error)
    }
  }
}
</script>

<template>
  <div class="app-container">
    <el-card shadow="never" class="operate-container">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAdd()">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="flashSessionTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="타임 세일 시간대 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="일일 시작 시간" align="center">
          <template #default="scope">{{ formatTime(scope.row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="일일 종료 시간" align="center">
          <template #default="scope">{{ formatTime(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="사용" align="center">
          <template #default="scope">
            <el-switch @change="handleStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.status">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">편집
            </el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog title="시간대 추가" v-model="dialogVisible" width="40%">
      <el-form :model="flashSession" ref="flashSessionForm" label-width="150px">
        <el-form-item label="타임 세일 시간대 이름:">
          <el-input v-model="flashSession.name" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="일일 시작 시간:">
          <el-time-picker v-model="flashSession.startTime" placeholder="시간을 선택하세요">
          </el-time-picker>
        </el-form-item>
        <el-form-item label="일일 종료 시간:">
          <el-time-picker v-model="flashSession.endTime" placeholder="시간을 선택하세요">
          </el-time-picker>
        </el-form-item>
        <el-form-item label="사용 여부">
          <el-radio-group v-model="flashSession.status">
            <el-radio :label="1">사용</el-radio>
            <el-radio :label="0">사용 안 함</el-radio>
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

<style scoped>
.operate-container {
  margin-top: 0;
}
</style>

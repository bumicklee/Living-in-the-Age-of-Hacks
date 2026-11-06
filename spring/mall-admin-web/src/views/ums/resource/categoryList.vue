<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { resourceCategoryListAllAPI, resourceCategoryCreateAPI, resourceCategoryUpdateAPI, resourceCategoryDeleteByIdAPI } from '@/apis/resourceCategory'
import type { UmsResourceCategory } from '@/types/resource'

// 리소스 카테고리 목록 데이터
const list = ref<UmsResourceCategory[]>([])
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await resourceCategoryListAllAPI()
    listLoading.value = false
    list.value = res.data
  } catch (error) {
    listLoading.value = false
    console.error('리소스 카테고리 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
})

// 기본 리소스 카테고리 객체
const defaultResourceCategory: UmsResourceCategory = {
  name: '',
  sort: 0
}
// 현재 작업 중인 리소스 분류
const resourceCategory = ref(Object.assign({}, defaultResourceCategory))
// 편집창 표시 여부
const dialogVisible = ref(false)
// 편집 상태
const isEdit = ref(false)

// 리소스 카테고리 추가
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  resourceCategory.value = Object.assign({}, defaultResourceCategory)
}

// 리소스 분류 업데이트
const handleUpdate = (index: number, row: UmsResourceCategory) => {
  dialogVisible.value = true
  isEdit.value = true
  resourceCategory.value = Object.assign({}, row)
}

// 리소스 카테고리 삭제
const handleDelete = async (index: number, row: UmsResourceCategory) => {
  await ElMessageBox.confirm('이 카테고리를 삭제할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
  })
  try {
    await resourceCategoryDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    console.error('리소스 카테고리 삭제 실패:', error)
  }
}

// 대화 상자 확인 처리
const handleDialogConfirm = async () => {
  await ElMessageBox.confirm('확인할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
  })
  if (isEdit.value) {
    try {
      await resourceCategoryUpdateAPI(resourceCategory.value.id!, resourceCategory.value)
      ElMessage.success('수정 성공!')
      dialogVisible.value = false
      getList()
    } catch (error) {
      console.error('리소스 분류 업데이트 실패:', error)
    }
  } else {
    try {
      await resourceCategoryCreateAPI(resourceCategory.value)
      ElMessage.success('추가 성공!')
      dialogVisible.value = false
      getList()
    } catch (error) {
      console.error('리소스 카테고리 생성 실패:', error)
    }
  }

}

// 날짜 포맷 필터 함수
const formatDateTime = (time: string) => {
  if (!time) {
    return 'N/A'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<template>
  <div class="app-container">
    <el-card shadow="never" class="operate-container">
      <el-icon class="el-icon-middle">
        <tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAdd()">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="resourceCategoryTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="생성 시간" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="정렬" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
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
    <el-dialog title="카테고리 추가" v-model="dialogVisible" width="40%">
      <el-form :model="resourceCategory" ref="resourceCategoryForm" label-width="150px" size="small">
        <el-form-item label="이름:">
          <el-input v-model="resourceCategory.name" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="정렬:">
          <el-input v-model="resourceCategory.sort" style="width: 250px"></el-input>
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

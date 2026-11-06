<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { getMenuListByParentIdAPI, deleteMenuByIdAPI, menuUpdateHiddenByIdAPI } from '@/apis/menu.ts'
import type { UmsMenu } from '@/types/menu'
import type { PageParam } from '@/types/common'

// 라우트 객체 조회
const router = useRouter()
const route = useRoute()

// 메뉴 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10
})
// 메뉴 목록 데이터
const list = ref<UmsMenu[]>([])
// 상위 메뉴ID
const parentId = ref(0)
// 전체 페이지 수
const total = ref<number>()
// 테이블 로딩 상태
const listLoading = ref(true)
// 상위 초기화ID
const resetParentId = () => {
  listQuery.value.pageNum = 1
  if (route.query.parentId) {
    parentId.value = Number(route.query.parentId)
  } else {
    parentId.value = 0
  }
}
// 메뉴 목록 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getMenuListByParentIdAPI(parentId.value, listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error('메뉴 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 후 실행
onMounted(() => {
  resetParentId()
  getList()
})

// 메뉴 추가 처리
const handleAddMenu = () => {
  router.push('/ums/addMenu')
}

// 페이지당 건수 변경 처리
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

// 숨김 상태 변경 처리
const handleHiddenChange = async (index: number, row: UmsMenu) => {
  await menuUpdateHiddenByIdAPI(row.id!, { hidden: row.hidden })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

// 하위 항목 표시 처리
const handleShowNextLevel = (index: number, row: UmsMenu) => {
  router.push({ path: '/ums/menu', query: { parentId: row.id } })
}

// 갱신 처리
const handleUpdate = (index: number, row: UmsMenu) => {
  router.push({ path: '/ums/updateMenu', query: { id: row.id } })
}

// 삭제 처리
const handleDelete = async (index: number, row: UmsMenu) => {
  await ElMessageBox.confirm('이 메뉴를 삭제할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
  })
  try {
    await deleteMenuByIdAPI(row.id!)
    ElMessage({
      message: '삭제 성공',
      type: 'success',
      duration: 1000
    })
    getList()
  } catch (error) {
    console.error('메뉴 삭제 실패:', error)
  }
}

// 라우트 변경 감시
watch(() => route, () => {
  resetParentId()
  getList()
}, { deep: true })

// 등급 필터
const levelFilter = (value: number) => {
  if (value === 0) {
    return '1단계'
  } else if (value === 1) {
    return '2단계'
  }
  return ''
}

// 하위 필터 비활성화
const disableNextLevel = (value: number) => {
  if (value === 0) {
    return false
  } else {
    return true
  }
}
</script>

<template>
  <div class="app-container">
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAddMenu()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="menuTable" style="width: 100%" :data="list" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="메뉴 이름" align="center">
          <template #default="scope">{{ scope.row.title }}</template>
        </el-table-column>
        <el-table-column label="메뉴 단계" width="100" align="center">
          <template #default="scope">{{ levelFilter(scope.row.level) }}</template>
        </el-table-column>
        <el-table-column label="프런트엔드 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="프런트엔드 아이콘" width="100" align="center">
          <template #default="scope"><svg-icon :icon-class="scope.row.icon"></svg-icon></template>
        </el-table-column>
        <el-table-column label="표시 여부" width="100" align="center">
          <template #default="scope">
            <el-switch @change="handleHiddenChange(scope.$index, scope.row)" :active-value="0" :inactive-value="1"
              v-model="scope.row.hidden">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="설정" width="120" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" link :disabled="disableNextLevel(scope.row.level)"
              @click="handleShowNextLevel(scope.$index, scope.row)">하위 보기
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">편집
            </el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" :page-size="listQuery.pageSize" :page-sizes="[10, 15, 20]"
        v-model:current-page="listQuery.pageNum" :total="total">
      </el-pagination>
    </div>
  </div>
</template>

<style scoped></style>

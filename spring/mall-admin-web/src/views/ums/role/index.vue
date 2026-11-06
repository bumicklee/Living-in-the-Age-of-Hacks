<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getRoleListAPI, roleCreateAPI, roleUpdateByIdAPI, roleUpdateStatusAPI, roleDeleteByIdsAPI } from '@/apis/role'
import type { UmsRole } from '@/types/role'
import type { PageParam } from '@/types/common'

// 라우트 객체 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})
// 역할 목록 데이터
const list = ref<UmsRole[]>([])
// 테이블 로딩 상태
const listLoading = ref(false)
// 전체 페이지 수
const total = ref(0)
// 역할 목록 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getRoleListAPI(listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error('역할 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 후 데이터 초기화
onMounted(() => {
  getList()
})

// 현재 작업 중인 역할 객체
const role = ref<UmsRole>({
  name: '',
  adminCount: 0,
  status: 1
})
// 편집 대화상자 표시 여부
const dialogVisible = ref(false)
// 편집 상태 여부
const isEdit = ref(false)

// 검색 조건 초기화
const handleResetSearch = () => {
  listQuery.value.pageNum = 1
  listQuery.value.keyword = ''
}

// 검색 처리
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

// 역할 추가 처리
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  role.value = {
    name: '',
    adminCount: 0,
    status: 1
  }
}

// 상태 변경 처리
const handleStatusChange = async (index: number, row: UmsRole) => {
  try {
    await ElMessageBox.confirm('이 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await roleUpdateStatusAPI(row.id!, { status: row.status })
    ElMessage.success('수정 성공!')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('상태 업데이트 실패:', error)
      // 취소되거나 실패하면 목록을 다시 조회해 상태를 복원
      getList()
    }
  }
}

// 삭제 처리
const handleDelete = async (index: number, row: UmsRole) => {
  try {
    await ElMessageBox.confirm('이 역할을 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    const ids = []
    ids.push(row.id)
    await roleDeleteByIdsAPI({ ids: ids.toString() })
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('삭제 실패:', error)
    }
  }
}

// 갱신 처리
const handleUpdate = (index: number, row: UmsRole) => {
  dialogVisible.value = true
  isEdit.value = true
  // row 객체를 깊은 복사하여 원본 데이터 직접 수정 방지
  role.value = { ...row }
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
      await roleUpdateByIdAPI(role.value.id!, role.value)
      ElMessage.success('수정 성공!')
    } else {
      await roleCreateAPI(role.value)
      ElMessage.success('추가 성공!')
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('작업 실패:', error)
    }
  }
}

// 메뉴 선택 처리
const handleSelectMenu = (index: number, row: UmsRole) => {
  router.push({ path: '/ums/allocMenu', query: { roleId: row.id } })
}

// 리소스 선택 처리
const handleSelectResource = (index: number, row: UmsRole) => {
  router.push({ path: '/ums/allocResource', query: { roleId: row.id } })
}

// 날짜 포맷 함수
const formatDateTime = (time: string) => {
  if (!time) {
    return 'N/A'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
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
        <el-button style="float: right" @click="handleSearchList()" type="primary" size="default">
          조회 검색
        </el-button>
        <el-button style="float: right; margin-right: 15px" @click="handleResetSearch()" size="default">
          초기화
        </el-button>
      </div>
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" size="default" label-width="140px">
          <el-form-item label="검색어 입력:">
            <el-input v-model="listQuery.keyword" class="input-width" placeholder="역할 이름" clearable></el-input>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button size="default" class="btn-add" @click="handleAdd()" style="margin-left: 20px">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="roleTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="역할 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="설명" align="center">
          <template #default="scope">{{ scope.row.description }}</template>
        </el-table-column>
        <el-table-column label="사용자 수" width="100" align="center">
          <template #default="scope">{{ scope.row.adminCount }}</template>
        </el-table-column>
        <el-table-column label="추가 시간" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="사용 여부" width="140" align="center">
          <template #default="scope">
            <el-switch @change="handleStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.status">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="160" align="center">
          <template #default="scope">
            <el-row>
              <el-col :span="12">
                <el-button size="small" type="primary" link @click="handleSelectMenu(scope.$index, scope.row)">메뉴 할당
                </el-button>
              </el-col>
              <el-col :span="12">
                <el-button size="small" type="primary" link @click="handleSelectResource(scope.$index, scope.row)">리소스 할당
                </el-button>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-button size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">
                  편집
                </el-button>
              </el-col>
              <el-col :span="12">
                <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
                </el-button>
              </el-col>
            </el-row>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '역할 편집' : '역할 추가'" width="40%">
      <el-form :model="role" label-width="150px" size="default">
        <el-form-item label="역할 이름:">
          <el-input v-model="role.name" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="설명:">
          <el-input v-model="role.description" type="textarea" :rows="5" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="사용 여부:">
          <el-radio-group v-model="role.status">
            <el-radio :label="1">예</el-radio>
            <el-radio :label="0">아니요</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" size="default">취 소</el-button>
          <el-button type="primary" @click="handleDialogConfirm()" size="default">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style></style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getAdminListAPI, adminRegisterAPI, adminUpdateByIdAPI, adminUpdateStatusByIdAPI, adminDeleteByIdAPI, getRoleByAdminIdAPI, adminRoleUpdateAPI } from '@/apis/admin.ts'
import { getRoleListAllAPI } from '@/apis/role.ts'
import type { UmsAdmin } from '@/types/admin'
import type { PageParam } from '@/types/common'
import type { UmsRole } from '@/types/role'

// 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})
// 관리자 목록 데이터
const list = ref<UmsAdmin[]>([])
// 전체 역할 목록
const allRoleList = ref<UmsRole[]>([])
// 테이블 로딩 상태
const listLoading = ref(true)
// 전체 페이지 수
const total = ref(0)
// 관리자 목록 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getAdminListAPI(listQuery.value)
    listLoading.value = false
    list.value = res.data.list
    total.value = res.data.total
  } catch (error) {
    listLoading.value = false
    console.error('관리자 목록 조회 실패:', error)
  }
}
// 전체 역할 목록 조회
const getAllRoleList = async () => {
  try {
    const response = await getRoleListAllAPI()
    allRoleList.value = response.data
  } catch (error) {
    console.error('역할 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 후 데이터 초기화
onMounted(() => {
  getList()
  getAllRoleList()
})

// 현재 작업 중인 관리자
const admin = ref<UmsAdmin>({
  username: '',
  password: '',
  status: 1
})
// 관리자 편집 대화상자 표시 여부
const dialogVisible = ref(false)
// 편집 상태 여부
const isEdit = ref(false)

// 역할 할당 대화상자 표시 여부
const allocDialogVisible = ref(false)
// 현재 역할을 할당 중인 관리자ID
const allocAdminId = ref<number>()
// 현재 관리자에게 할당된 역할ID
const allocRoleIds = ref<number[]>([])

// 관리자 ID로 역할 목록 조회
const getRoleListByAdmin = async (adminId: number) => {
  try {
    const res = await getRoleByAdminIdAPI(adminId)
    const allocRoleList = res.data
    allocRoleIds.value = []
    allocRoleList.forEach((item) => allocRoleIds.value.push(item.id!))
  } catch (error) {
    console.error('관리자 역할 목록 조회 실패:', error)
  }
}

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

// 관리자 추가 처리
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  admin.value = {
    username: '',
    password: '',
    status: 1
  }
}

// 상태 변경 처리
const handleStatusChange = async (index: number, row: UmsAdmin) => {
  try {
    await ElMessageBox.confirm('이 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await adminUpdateStatusByIdAPI(row.id!, { status: row.status })
    ElMessage.success('수정 성공!')
  } catch (error) {
    console.error('상태 업데이트 실패:', error)
    // 취소되거나 실패하면 목록을 다시 조회해 상태를 복원
    getList()
  }
}

// 삭제 처리
const handleDelete = async (index: number, row: UmsAdmin) => {
  try {
    await ElMessageBox.confirm('이 사용자를 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await adminDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    console.error('삭제 실패:', error)
  }
}

// 갱신 처리
const handleUpdate = (index: number, row: UmsAdmin) => {
  dialogVisible.value = true
  isEdit.value = true
  // row 객체를 깊은 복사하여 원본 데이터 직접 수정 방지
  admin.value = Object.assign({}, row)
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
      await adminUpdateByIdAPI(admin.value.id!, admin.value)
      ElMessage.success('수정 성공!')
    } else {
      await adminRegisterAPI(admin.value)
      ElMessage.success('추가 성공!')
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    console.error('작업 실패:', error)
  }
}

// 역할 할당 대화 상자 확인 처리
const handleAllocDialogConfirm = async () => {
  try {
    await ElMessageBox.confirm('확인할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await adminRoleUpdateAPI({ adminId: allocAdminId.value!, roleIds: allocRoleIds.value.join(',') })
    ElMessage.success('할당 성공!')
    allocDialogVisible.value = false
  } catch (error) {
    console.error('역할 할당 실패:', error)
  }
}

// 역할 선택 처리
const handleSelectRole = (index: number, row: UmsAdmin) => {
  allocAdminId.value = row.id!
  allocDialogVisible.value = true
  getRoleListByAdmin(allocAdminId.value)
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
    <el-card class="filter-container" shadow="never">
      <div>
        <el-icon class="el-icon-middle">
          <Search />
        </el-icon>
        <span>필터 검색</span>
        <el-button data-testid="member-search" style="float: right" @click="handleSearchList()" type="primary" size="default">
          조회 검색
        </el-button>
        <el-button style="float: right; margin-right: 15px" @click="handleResetSearch()" size="default">
          초기화
        </el-button>
      </div>
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" size="default" label-width="140px">
          <el-form-item label="검색어 입력:">
            <el-input data-testid="member-search-keyword" v-model="listQuery.keyword" class="input-width" placeholder="계정/이름" clearable></el-input>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button data-testid="member-add" class="btn-add" @click="handleAdd()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table data-testid="member-table" ref="adminTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="계정" align="center">
          <template #default="scope">{{ scope.row.username }}</template>
        </el-table-column>
        <el-table-column label="이름" align="center">
          <template #default="scope">{{ scope.row.nickName }}</template>
        </el-table-column>
        <el-table-column label="이메일" align="center">
          <template #default="scope">{{ scope.row.email }}</template>
        </el-table-column>
        <el-table-column label="추가 시간" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="마지막 로그인" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.loginTime) }}</template>
        </el-table-column>
        <el-table-column label="사용 여부" width="140" align="center">
          <template #default="scope">
            <el-switch @change="handleStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.status">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button :data-testid="'member-role-' + scope.row.id" size="small" type="primary" link @click="handleSelectRole(scope.$index, scope.row)">역할 할당
            </el-button>
            <el-button :data-testid="'member-edit-' + scope.row.id" size="small" type="primary" link @click="handleUpdate(scope.$index, scope.row)">
              편집
            </el-button>
            <el-button :data-testid="'member-delete-' + scope.row.id" size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-container">
      <el-pagination data-testid="member-pagination" background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" v-model:current-page="listQuery.pageNum"
        :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="total">
      </el-pagination>
    </div>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '사용자 편집' : '사용자 추가'" width="40%">
      <el-form :model="admin" label-width="150px" size="default">
        <el-form-item label="계정:">
          <el-input v-model="admin.username" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="이름:">
          <el-input v-model="admin.nickName" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="이메일:">
          <el-input v-model="admin.email" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="비밀번호:" v-if="!isEdit">
          <el-input v-model="admin.password" type="password" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="메모:">
          <el-input v-model="admin.note" type="textarea" :rows="5" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="사용 여부:">
          <el-radio-group v-model="admin.status">
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
    <el-dialog title="역할 할당" v-model="allocDialogVisible" width="30%">
      <el-select v-model="allocRoleIds" multiple placeholder="선택하세요" size="default" style="width: 80%">
        <el-option v-for="item in allRoleList" :key="item.id" :label="item.name" :value="item.id!">
        </el-option>
      </el-select>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="allocDialogVisible = false" size="default">취 소</el-button>
          <el-button type="primary" @click="handleAllocDialogConfirm()" size="default">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Tickets } from '@element-plus/icons-vue'
import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
import { getResourceListAPI, resourceCreateAPI, resourceUpdateAPI, resourceDeleteByIdAPI } from '@/apis/resource'
import { resourceCategoryListAllAPI } from '@/apis/resourceCategory'
import type { ResourceQueryParam, UmsResource } from '@/types/resource'

// 라우트 정보 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<ResourceQueryParam>({
  pageNum: 1,
  pageSize: 10,
})
// 리소스 목록 데이터
const list = ref<UmsResource[]>([])
// 전체 페이지 수
const total = ref<number>(0)
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getResourceListAPI(listQuery.value)
    listLoading.value = false
    list.value = res.data.list
    total.value = res.data.total
  } catch (error) {
    listLoading.value = false
    console.error('리소스 목록 조회 실패:', error)
  }
}

// 카테고리 필터 옵션
const categoryOptions = ref<{ label: string; value: number }[]>([])
// 기본 선택 카테고리ID
const defaultCategoryId = ref<number>()
// 카테고리 목록 조회
const getCateList = async () => {
  try {
    const res = await resourceCategoryListAllAPI()
    const cateList = res.data
    cateList.forEach(item => {
      categoryOptions.value.push({ label: item.name, value: item.id! })
    })
    if (cateList && cateList.length > 0) {
      defaultCategoryId.value = cateList[0]!.id
    }
  } catch (error) {
    console.error('리소스 카테고리 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
  getCateList()
})

// 기본 리소스 객체
const defaultResource: UmsResource = {
  name: '',
  url: '',
  description: '',
  categoryId: 0
}
// 현재 작업 중인 리소스
const resource = ref(Object.assign({}, defaultResource))
// 편집 팝업 표시 여부
const dialogVisible = ref(false)
// 편집 상태 여부
const isEdit = ref(false)

// 검색 조건 초기화
const handleResetSearch = () => {
  listQuery.value = {
    pageNum: 1,
    pageSize: 10,
  }
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

// 리소스 추가
const handleAdd = () => {
  dialogVisible.value = true
  isEdit.value = false
  resource.value = Object.assign({}, defaultResource)
  if (defaultCategoryId.value) {
    resource.value.categoryId = defaultCategoryId.value
  }
}

// 리소스 삭제
const handleDelete = async (index: number, row: UmsResource) => {
  await ElMessageBox.confirm('이 리소스를 삭제할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
  })
  try {
    await resourceDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    console.error('리소스 삭제 실패:', error)
  }
}

// 리소스 업데이트
const handleUpdate = (index: number, row: UmsResource) => {
  dialogVisible.value = true
  isEdit.value = true
  resource.value = Object.assign({}, row)
}

// 대화 상자 확인 처리
const handleDialogConfirm = async () => {
  await ElMessageBox.confirm('확인할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
  })
  try {
    if (isEdit.value) {
      await resourceUpdateAPI(resource.value.id!, resource.value)
      ElMessage.success('수정 성공!')
      dialogVisible.value = false
      getList()
    } else {
      await resourceCreateAPI(resource.value)
      ElMessage.success('추가 성공!')
      dialogVisible.value = false
      getList()
    }
  } catch (error) {
    console.error('작업 실패:', error)
  }
}

// 리소스 분류 표시
const handleShowCategory = () => {
  router.push({ path: '/ums/resourceCategory' })
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
        <el-button style="float:right" type="primary" @click="handleSearchList()">
          조회 검색
        </el-button>
        <el-button style="float:right;margin-right: 15px" @click="handleResetSearch()">
          초기화
        </el-button>
      </div>
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="리소스 이름:">
            <el-input v-model="listQuery.nameKeyword" class="input-width" placeholder="리소스 이름" clearable></el-input>
          </el-form-item>
          <el-form-item label="리소스 경로:">
            <el-input v-model="listQuery.urlKeyword" class="input-width" placeholder="리소스 경로" clearable></el-input>
          </el-form-item>
          <el-form-item label="리소스 카테고리:">
            <el-select v-model="listQuery.categoryId" placeholder="전체" clearable class="input-width"
              style="width: 150px;">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <i class="el-icon-tickets"></i>
      <el-icon class="el-icon-middle">
        <tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAdd()" style="margin-left: 20px">추가</el-button>
      <el-button class="btn-add" @click="handleShowCategory()">리소스 카테고리</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="resourceTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="리소스 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="리소스 경로" align="center">
          <template #default="scope">{{ scope.row.url }}</template>
        </el-table-column>
        <el-table-column label="설명" align="center">
          <template #default="scope">{{ scope.row.description }}</template>
        </el-table-column>
        <el-table-column label="추가 시간" width="160" align="center">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="140" align="center">
          <template #default="scope">
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
        :page-size="listQuery.pageSize" :page-sizes="[10, 15, 20]" :total="total">
      </el-pagination>
    </div>
    <el-dialog :title="isEdit ? '리소스 편집' : '리소스 추가'" v-model="dialogVisible" width="40%">
      <el-form :model="resource" ref="resourceForm" label-width="150px">
        <el-form-item label="리소스 이름:">
          <el-input v-model="resource.name" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="리소스 경로:">
          <el-input v-model="resource.url" style="width: 250px"></el-input>
        </el-form-item>
        <el-form-item label="리소스 카테고리:">
          <el-select v-model="resource.categoryId" placeholder="전체" clearable style="width: 250px">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="설명:">
          <el-input v-model="resource.description" type="textarea" :rows="5" style="width: 250px"></el-input>
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

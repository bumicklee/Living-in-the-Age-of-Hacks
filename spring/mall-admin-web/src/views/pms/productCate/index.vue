<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { getProductCategoryListAPI, productCategoryDeleteByIdAPI, productCategoryUpdateShowStatusAPI, productCategoryUpdateNavStatusAPI } from '@/apis/productCate'
import type { PmsProductCategory } from '@/types/productCate'

// 라우트 조회
const router = useRouter()
const route = useRoute()

// 현재 목록 페이지의 상위 카테고리ID
const parentId = ref(0)
// 목록 조회 파라미터
const listQuery = ref({
  pageNum: 1,
  pageSize: 10
})
// 목록 데이터
const list = ref<PmsProductCategory[]>([])
// 총 건수
const total = ref(0)
// 로딩 상태
const listLoading = ref(true)
// 상위 단계 초기화ID
const resetParentId = () => {
  listQuery.value.pageNum = 1
  if (route.query.parentId != null) {
    parentId.value = Number(route.query.parentId)
  } else {
    parentId.value = 0
  }
}
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  const res = await getProductCategoryListAPI(parentId.value, listQuery.value)
  listLoading.value = false
  list.value = res.data.list
  total.value = res.data.total
}

// 컴포넌트 마운트 후 실행
onMounted(() => {
  resetParentId()
  getList()
})

// 조회 파라미터 변경 감시
watch(() => route.query, () => {
  resetParentId()
  getList()
})

// 상품 카테고리 추가
const handleAddProductCate = () => {
  router.push('/pms/addProductCate')
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

// 내비게이션 상태 변경 처리
const handleNavStatusChange = async (index: number, row: PmsProductCategory) => {
  await productCategoryUpdateNavStatusAPI({ ids: [row.id!].join(','), navStatus: row.navStatus })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

// 표시 상태 변경 처리
const handleShowStatusChange = async (index: number, row: PmsProductCategory) => {
  await productCategoryUpdateShowStatusAPI({ ids: [row.id!].join(','), showStatus: row.navStatus })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

// 하위 카테고리 보기
const handleShowNextLevel = (index: number, row: PmsProductCategory) => {
  router.push({ path: '/pms/productCate', query: { parentId: row.id } })
}

// 상품 이동
const handleTransferProduct = (index: number, row: PmsProductCategory) => {
  console.log('handleAddProductCate', row)
}

// 카테고리 업데이트
const handleUpdate = (index: number, row: PmsProductCategory) => {
  router.push({ path: '/pms/updateProductCate', query: { id: row.id } })
}

// 카테고리 삭제
const handleDelete = async (index: number, row: PmsProductCategory) => {
  await ElMessageBox.confirm('이 브랜드를 삭제할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await productCategoryDeleteByIdAPI(row.id!)
  ElMessage({
    message: '삭제 성공',
    type: 'success',
    duration: 1000
  })
  getList()
}

// 카테고리 레벨 필터
const levelFilter = (value: number) => {
  if (value === 0) {
    return '1단계'
  } else if (value === 1) {
    return '2단계'
  }
}

// 하위 버튼 비활성화
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
      <el-button class="btn-add" @click="handleAddProductCate()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="productCateTable" style="width: 100%" :data="list" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="카테고리 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="등급" width="100" align="center">
          <template #default="scope">{{ levelFilter(scope.row.level) }}</template>
        </el-table-column>
        <el-table-column label="상품 수량" width="100" align="center">
          <template #default="scope">{{ scope.row.productCount }}</template>
        </el-table-column>
        <el-table-column label="수량 단위" width="100" align="center">
          <template #default="scope">{{ scope.row.productUnit }}</template>
        </el-table-column>
        <el-table-column label="내비게이션 바" width="100" align="center">
          <template #default="scope">
            <el-switch @change="handleNavStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.navStatus">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="표시 여부" width="100" align="center">
          <template #default="scope">
            <el-switch @change="handleShowStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
              v-model="scope.row.showStatus">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="설정" width="200" align="center">
          <template #default="scope">
            <el-button size="small" :disabled="disableNextLevel(scope.row.level)"
              @click="handleShowNextLevel(scope.$index, scope.row)">하위 보기
            </el-button>
            <el-button size="small" @click="handleTransferProduct(scope.$index, scope.row)">상품 이동
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleUpdate(scope.$index, scope.row)">편집
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.$index, scope.row)">삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-container">
      <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]"
        v-model:current-page="listQuery.pageNum" :total="total">
      </el-pagination>
    </div>
  </div>
</template>

<style scoped></style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductAttributeListAPI, productAttributeDeleteByIds } from '@/apis/productAttr'
import { Tickets } from '@element-plus/icons-vue'
import type { PmsProductAttribute } from '@/types/productAttr'

// 라우트 객체 조회
const router = useRouter()

// 속성 카테고리ID
const cid = Number(router.currentRoute.value.query.cid)
// 속성 유형
const type = Number(router.currentRoute.value.query.type)
// 목록 조회 데이터
const listQuery = ref({
  pageNum: 1,
  pageSize: 5,
  type: type
})
const list = ref<PmsProductAttribute[]>([])
// 목록 데이터
const total = ref(0)
// 로딩 상태
const listLoading = ref(true)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getProductAttributeListAPI(cid, listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error(error)
  }
}

// 컴포넌트 마운트 후 실행
onMounted(() => {
  getList()
})

// 현재 일괄 작업 유형
const operateType = ref<string>()
// 일괄 작업 선택 항목
const multipleSelection = ref<PmsProductAttribute[]>([])
// 일괄 작업 유형 모음
const operates = ref([
  {
    label: "삭제",
    value: "deleteProductAttr"
  }
])

// 상품 속성 추가
const addProductAttr = () => {
  router.push({ path: '/pms/addProductAttr', query: { cid: router.currentRoute.value.query.cid, type: router.currentRoute.value.query.type } })
}

// 테이블 선택 상태 변경 처리
const handleSelectionChange = (val: PmsProductAttribute[]) => {
  multipleSelection.value = val
}

// 일괄 작업 처리
const handleBatchOperate = async () => {
  if (multipleSelection.value.length < 1) {
    ElMessage.warning('레코드를 하나 선택하세요')
    return
  }
  if (operateType.value !== 'deleteProductAttr') {
    ElMessage.warning('일괄 작업 유형을 선택하세요')
    return
  }
  await handleDeleteProductAttr(multipleSelection.value.map(item => item.id!))
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

// 갱신 작업 처리
const handleUpdate = (index: number, row: PmsProductAttribute) => {
  router.push({ path: '/pms/updateProductAttr', query: { id: row.id } })
}

// 상품 속성 삭제 처리
const handleDeleteProductAttr = async (ids: number[]) => {
  try {
    await ElMessageBox.confirm('이 속성을 삭제할까요?', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await productAttributeDeleteByIds({ ids: ids.join(",") })
    ElMessage.success('삭제 성공')
    getList()
  } catch (error) {
    console.error(error)
  }
}

// 삭제 작업 처리
const handleDelete = async (index: number, row: PmsProductAttribute) => {
  await handleDeleteProductAttr([row.id!])
}

// 속성값 입력 방식
const inputTypeFilter = (value: number) => {
  if (value === 1) {
    return '목록에서 선택'
  } else {
    return '직접 입력'
  }
}

// 속성 선택 가능 여부
const selectTypeFilter = (value: number) => {
  if (value === 1) {
    return '단일 선택'
  } else if (value === 2) {
    return '다중 선택'
  } else {
    return '고유'
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
      <el-button class="btn-add" @click="addProductAttr()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="productAttrTable" :data="list" style="width: 100%" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="속성명" width="140" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="상품 유형" width="140" align="center">
          <template>{{ $route.query.cname }}</template>
        </el-table-column>
        <el-table-column label="속성 선택 가능 여부" width="120" align="center">
          <template #default="scope">{{ selectTypeFilter(scope.row.selectType) }}</template>
        </el-table-column>
        <el-table-column label="속성값 입력 방식" width="150" align="center">
          <template #default="scope">{{ inputTypeFilter(scope.row.inputType) }}</template>
        </el-table-column>
        <el-table-column label="선택값 목록" align="center">
          <template #default="scope">{{ scope.row.inputList }}</template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
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

<style rel="stylesheet/scss" lang="scss" scoped></style>

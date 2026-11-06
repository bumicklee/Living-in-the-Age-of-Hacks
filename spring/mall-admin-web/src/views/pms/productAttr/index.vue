<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductAttributeCategoryListAPI, productAttributeCategoryCreateAPI, productAttributeCategoryDeleteById, productAttributeCategoryUpdateAPI } from '@/apis/productAttrCate'
import { Tickets } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import type { PageParam } from '@/types/common'
import type { PmsProductAttributeCategory } from '@/types/productAttr'

// 라우트 객체 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<PageParam>({
  pageNum: 1,
  pageSize: 10
})
// 목록 데이터
const list = ref<PmsProductAttributeCategory[]>([])
// 총 건수
const total = ref(0)
// 로딩 상태
const listLoading = ref(true)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getProductAttributeCategoryListAPI(listQuery.value)
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

// 현재 작업 중인 속성 분류
const productAttrCate = ref<PmsProductAttributeCategory>({
  name: ''
})
// 편집창 표시 여부
const dialogVisible = ref(false)
// 편집창 제목
const dialogTitle = ref('')
// 편집창의 폼 참조
const productAttrCatForm = ref<FormInstance>()
// 편집창의 검증 규칙
const rules = ref({
  name: [
    { required: true, message: '타입 이름을 입력하세요', trigger: 'blur' }
  ]
})

// 상품 속성 카테고리 추가
const addProductAttrCate = () => {
  dialogVisible.value = true
  dialogTitle.value = "유형 추가"
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

// 삭제 작업 처리
const handleDelete = async (index: number, row: PmsProductAttributeCategory) => {
  await ElMessageBox.confirm('이 브랜드를 삭제할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await productAttributeCategoryDeleteById(row.id!)
  ElMessage.success('삭제 성공')
  getList()
}

// 갱신 작업 처리
const handleUpdate = (index: number, row: PmsProductAttributeCategory) => {
  dialogVisible.value = true
  dialogTitle.value = "유형 편집"
  productAttrCate.value.name = row.name
  productAttrCate.value.id = row.id
}

// 속성 목록 조회
const getAttrList = (index: number, row: PmsProductAttributeCategory) => {
  router.push({ path: '/pms/productAttrList', query: { cid: row.id, cname: row.name, type: 0 } })
}

// 파라미터 목록 조회
const getParamList = (index: number, row: PmsProductAttributeCategory) => {
  router.push({ path: '/pms/productAttrList', query: { cid: row.id, cname: row.name, type: 1 } })
}

// 확인 대화상자 작업
const handleConfirm = async () => {
  if (!productAttrCatForm.value) return
  const valid = await productAttrCatForm.value.validate().catch(() => false)
  if (valid) {
    if (dialogTitle.value === "유형 추가") {
      await productAttributeCategoryCreateAPI(productAttrCate.value.name)
      ElMessage.success('추가 성공')
      dialogVisible.value = false
    } else {
      await productAttributeCategoryUpdateAPI(productAttrCate.value.id!, productAttrCate.value.name)
      ElMessage.success('수정 성공')
      dialogVisible.value = false
    }
    getList()
  } else {
    ElMessage.error('먼저 타입 이름을 입력해 주세요!')
    return false
  }
}

// 대화상자 닫기 처리
const handleClose = () => {
  dialogVisible.value = false
}
</script>

<template>
  <div class="app-container">
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="addProductAttrCate()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="productAttrCateTable" style="width: 100%" :data="list" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="유형 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="속성 수량" width="200" align="center">
          <template #default="scope">{{ scope.row.attributeCount == null ? 0 : scope.row.attributeCount }}</template>
        </el-table-column>
        <el-table-column label="파라미터 수량" width="200" align="center">
          <template #default="scope">{{ scope.row.paramCount == null ? 0 : scope.row.paramCount }}</template>
        </el-table-column>
        <el-table-column label="설정" width="200" align="center">
          <template #default="scope">
            <el-button size="small" @click="getAttrList(scope.$index, scope.row)">속성 목록
            </el-button>
            <el-button size="small" @click="getParamList(scope.$index, scope.row)">파라미터 목록
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
    <el-dialog :title="dialogTitle" v-model="dialogVisible" :before-close="handleClose" width="30%">
      <el-form ref="productAttrCatForm" :model="productAttrCate" :rules="rules" label-width="120px">
        <el-form-item label="유형 이름" prop="name">
          <el-input v-model="productAttrCate.name" autocomplete="off"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleConfirm">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style rel="stylesheet/scss" lang="scss" scoped></style>

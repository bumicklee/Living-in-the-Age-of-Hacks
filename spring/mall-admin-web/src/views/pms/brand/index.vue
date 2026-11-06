<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type Action } from 'element-plus'
import { getBrandListAPI, brandUpdateShowStatusAPI, brandUpdateFactoryStatusAPI, brandDeleteByIdAPI } from '@/apis/brand'
import { Search, Tickets } from '@element-plus/icons-vue'
import type { PmsBrand } from '@/types/brand'
import type { PageParam } from '@/types/common'

// 라우트 객체 조회
const router = useRouter()

// 브랜드 목록 조회 파라미터
const listQuery = ref<PageParam>({
  keyword: '',
  pageNum: 1,
  pageSize: 10
})
// 브랜드 목록 데이터
const list = ref<PmsBrand[]>([])
// 테이블에서 선택된 행
const multipleSelection = ref<PmsBrand[]>([])
// 테이블 데이터 로딩 진행 표시줄
const listLoading = ref(true)
// 페이지네이션 컴포넌트 파라미터
const total = ref(0)
// 브랜드 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getBrandListAPI(listQuery.value)
    listLoading.value = false
    list.value = res.data.list
    total.value = res.data.total
  } catch (error) {
    listLoading.value = false
    console.error('브랜드 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 후 목록 정보 초기화
onMounted(() => {
  getList()
})

// 브랜드 검색 처리
const handleSearchBrand = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 브랜드 추가 처리
const handleAddBrand = () => {
  router.push({ path: '/pms/addBrand' })
}

// 브랜드 편집 작업 처리
const handleUpdateBrand = (index: number, row: PmsBrand) => {
  router.push({ path: '/pms/updateBrand', query: { id: row.id } })
}

// 브랜드 삭제 작업 처리
const handleDeleteBrand = async (index: number, row: PmsBrand) => {
  ElMessageBox.confirm('이 브랜드를 삭제할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning',
    callback: async (action: Action) => {
      if (action === 'confirm') {
        await brandDeleteByIdAPI(row.id!)
        ElMessage.success('삭제 성공')
        getList()
      }
    },
  })
}

// 테이블 선택 상태 변경 처리
const handleSelectionChange = (val: PmsBrand[]) => {
  multipleSelection.value = val
}

// 상품 목록 조회
const getProductList = (index: number, row: PmsBrand) => {
  console.log(index, row)
}

// 상품 리뷰 목록 조회
const getProductCommentList = (index: number, row: PmsBrand) => {
  console.log(index, row)
}

// 제조사 상태 변경 처리
const handleFactoryStatusChange = async (index: number, row: PmsBrand) => {

  try {
    await brandUpdateFactoryStatusAPI({
      ids: row.id!.toString(),
      factoryStatus: row.factoryStatus
    })
    ElMessage.success('수정 성공')
  } catch (error) {
    // 갱신에 실패하면 상태를 롤백
    row.factoryStatus = row.factoryStatus === 0 ? 1 : 0
    console.error('제조사 상태 업데이트 실패:', error)
  }
}

// 표시 상태 변경 처리
const handleShowStatusChange = async (index: number, row: PmsBrand) => {
  try {
    await brandUpdateShowStatusAPI({
      ids: row.id!.toString(),
      showStatus: row.showStatus
    })
    ElMessage.success('수정 성공')
  } catch (error) {
    // 갱신에 실패하면 상태를 롤백
    row.showStatus = row.showStatus === 0 ? 1 : 0
    console.error('표시 상태 업데이트 실패:', error)
  }
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

// 일괄 작업 유형
const operates = ref([
  {
    label: "브랜드 표시",
    value: "showBrand"
  },
  {
    label: "브랜드 숨기기",
    value: "hideBrand"
  }
])

// 하단 다중 선택 일괄 작업 유형
const operateType = ref<string | null>()

// 일괄 작업 처리
const handleBatchOperate = async () => {
  if (!multipleSelection.value || multipleSelection.value.length < 1) {
    ElMessage.warning('레코드를 하나 선택하세요')
    return
  }
  let showStatus = 0
  if (operateType.value === 'showBrand') {
    showStatus = 1
  } else if (operateType.value === 'hideBrand') {
    showStatus = 0
  } else {
    ElMessage.warning('일괄 작업 유형을 선택하세요')
    return
  }
  const idsArr = multipleSelection.value.map(item => item.id)
  try {
    await brandUpdateShowStatusAPI({
      ids: idsArr.join(','),
      showStatus: showStatus
    })
    getList()
    ElMessage.success('수정 성공')
  } catch (error) {
    console.error('일괄 작업 실패:', error)
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
        <el-button style="float: right" @click="handleSearchBrand()" type="primary">
          조회 결과
        </el-button>
      </div>
      <div style="margin-top: 15px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="검색어 입력:">
            <el-input style="width: 203px" v-model="listQuery.keyword" placeholder="브랜드명/키워드"></el-input>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleAddBrand()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="brandTable" :data="list" style="width: 100%" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="브랜드명" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="브랜드 첫 글자" width="100" align="center">
          <template #default="scope">{{ scope.row.firstLetter }}</template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="브랜드 제조사" width="100" align="center">
          <template #default="scope">
            <el-switch @change="handleFactoryStatusChange(scope.$index, scope.row)" :active-value="1"
              :inactive-value="0" v-model="scope.row.factoryStatus">
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
        <el-table-column label="관련" width="220" align="center">
          <template #default="scope">
            <span>상품:</span>
            <el-button size="small" type="primary" link @click="getProductList(scope.$index, scope.row)">100
            </el-button>
            <span>리뷰:</span>
            <el-button size="small" type="primary" link @click="getProductCommentList(scope.$index, scope.row)">1000
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleUpdateBrand(scope.$index, scope.row)">편집
            </el-button>
            <el-button size="small" type="danger" @click="handleDeleteBrand(scope.$index, scope.row)">삭제
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

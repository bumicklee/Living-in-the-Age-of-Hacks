<script setup lang="ts">
import { ref, onMounted, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProductListAPI,
  productUpdateDeleteStatusAPI,
  productUpdateNewStatusAPI,
  productUpdateRecommendStatusAPI,
  productUpdatePublishStatusAPI
} from '@/apis/product'
import { getSkuListByPidAPI, skuUpdateByPidAPI } from '@/apis/skuStock'
import { getProductAttributeListAPI } from '@/apis/productAttr'
import { getBrandListAPI } from '@/apis/brand'
import { getProductCategoryListWithChildrenAPI } from '@/apis/productCate'
import { Search, Tickets, Edit } from '@element-plus/icons-vue'
import type { PmsProduct, ProductQueryParam } from '@/types/product'
import type { ElCascaderDataVo, ElSelectDataVo } from '@/types/common'
import type { PmsSkuStock } from '@/types/skuStock'
import type { PmsProductAttribute } from '@/types/productAttr'

// 라우트 조회
const router = useRouter()

// 목록 조회 파라미터
const listQuery = ref<ProductQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 목록 데이터
const list = ref<PmsProduct[]>([])
// 총 건수
const total = ref(0)
// 로딩 상태
const listLoading = ref(true)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const response = await getProductListAPI(listQuery.value)
    listLoading.value = false
    list.value = response.data.list
    total.value = response.data.total
  } catch (error) {
    listLoading.value = false
    console.error(error)
  }
}
// 필터 검색의 브랜드 데이터
const brandOptions = ref<ElSelectDataVo[]>([])
// 브랜드 목록 데이터 조회
const getBrandList = async () => {
  const res = await getBrandListAPI({ pageNum: 1, pageSize: 100 })
  brandOptions.value = res.data.list.map(item => ({ label: item.name, value: item.id!.toString() }))
}
// 필터 검색의 상품 카테고리 데이터
const productCateOptions = ref<ElCascaderDataVo[]>([])
// 필터 검색에서 현재 선택된 상품 카테고리, 구조: [상위 카테고리 ID, 카테고리ID]
const selectProductCateValue = ref([])
// 상품 카테고리 목록 데이터 조회
const getProductCateList = async () => {
  const res = await getProductCategoryListWithChildrenAPI()
  const list = res.data
  productCateOptions.value = list.map(item => ({
    label: item.name,
    value: item.id!,
    children: item.children?.map(it => ({ label: it.name, value: it.id! }))
  }))
}
// 필터 검색의 판매/판매 중지 상태
const publishStatusOptions = ref([
  {
    value: 1,
    label: '판매 중'
  },
  {
    value: 0,
    label: '판매 중지'
  }
])
// 필터 검색의 심사 상태
const verifyStatusOptions = ref([
  {
    value: 1,
    label: '심사 통과'
  },
  {
    value: 0,
    label: '미심사'
  }
])

// 변경을 감시하여 목록 조회의 productCategoryId 데이터 수정
watch(selectProductCateValue, (newValue) => {
  if (newValue != null && newValue.length == 2) {
    listQuery.value.productCategoryId = newValue[1]
  } else {
    listQuery.value.productCategoryId = undefined
  }
}, { immediate: true })

// 컴포넌트 마운트 후 실행
onMounted(() => {
  getList()
  getBrandList()
  getProductCateList()
})

// 일괄 작업 유형
const operates = ref([
  {
    label: "상품 판매 개시",
    value: "publishOn"
  },
  {
    label: "상품 판매 중지",
    value: "publishOff"
  },
  {
    label: "추천으로 설정",
    value: "recommendOn"
  },
  {
    label: "추천 해제",
    value: "recommendOff"
  },
  {
    label: "신상품으로 설정",
    value: "newOn"
  },
  {
    label: "신상품 해제",
    value: "newOff"
  },
  {
    label: "휴지통으로 이동",
    value: "recycle"
  },
  {
    label: "카테고리로 이동",
    value: "transferCategory"
  }
])
// 현재 일괄 작업
const operateType = ref<string>()
// 현재 선택된 항목
const multipleSelection = ref<PmsProduct[]>([])

// SKU재고 팝업 데이터
const editSkuInfo = reactive({
  dialogVisible: false,
  productId: 0,
  productSn: '',
  productAttributeCategoryId: 0,
  stockList: [] as PmsSkuStock[],
  productAttr: [] as PmsProductAttribute[],
  keyword: undefined
})

// PmsSkuStock의 spData에서 규격에 해당하는 값 조회
const getProductSkuSp = (row: PmsSkuStock, index: number) => {
  const spData = JSON.parse(row.spData!)
  if (spData && index < spData.length) {
    return spData[index].value
  } else {
    return ''
  }
}

const handleShowSkuEditDialog = async (index: number, row: PmsProduct) => {
  editSkuInfo.dialogVisible = true
  editSkuInfo.productId = row.id!
  editSkuInfo.productSn = row.productSn
  editSkuInfo.productAttributeCategoryId = row.productAttributeCategoryId!
  editSkuInfo.keyword = undefined
  const resp = await getSkuListByPidAPI(row.id!, { keyword: editSkuInfo.keyword })
  editSkuInfo.stockList = resp.data
  if (row.productAttributeCategoryId) {
    const res2 = await getProductAttributeListAPI(row.productAttributeCategoryId, { pageNum: 1, pageSize: 10, type: 0 })
    editSkuInfo.productAttr = res2.data.list
  }
}

const handleSearchEditSku = async () => {
  const response = await getSkuListByPidAPI(editSkuInfo.productId, { keyword: editSkuInfo.keyword })
  editSkuInfo.stockList = response.data
}

const handleEditSkuConfirm = async () => {
  if (!editSkuInfo.stockList || editSkuInfo.stockList.length <= 0) {
    ElMessage({
      message: 'sku 정보 없음',
      type: 'warning',
      duration: 1000
    })
    return
  }
  await ElMessageBox.confirm('수정할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await skuUpdateByPidAPI(editSkuInfo.productId, editSkuInfo.stockList)
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
  editSkuInfo.dialogVisible = false
}

const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

const handleAddProduct = () => {
  router.push({ path: '/pms/addProduct' })
}

const handleBatchOperate = async () => {
  if (!operateType.value) {
    ElMessage({
      message: '작업 유형을 선택하세요',
      type: 'warning',
      duration: 1000
    })
    return
  }
  if (!multipleSelection.value || multipleSelection.value.length < 1) {
    ElMessage({
      message: '작업할 상품을 선택하세요',
      type: 'warning',
      duration: 1000
    })
    return
  }
  await ElMessageBox.confirm('이 일괄 작업을 진행할까요??', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  const ids = multipleSelection.value.map(item => item.id!)
  switch (operateType.value) {
    case operates.value[0]!.value:
      updatePublishStatus(1, ids)
      break
    case operates.value[1]!.value:
      updatePublishStatus(0, ids)
      break
    case operates.value[2]!.value:
      updateRecommendStatus(1, ids)
      break
    case operates.value[3]!.value:
      updateRecommendStatus(0, ids)
      break
    case operates.value[4]!.value:
      updateNewStatus(1, ids)
      break
    case operates.value[5]!.value:
      updateNewStatus(0, ids)
      break
    case operates.value[6]!.value:
      updateDeleteStatus(1, ids)
      break
    case operates.value[7]!.value:
      break
    default:
      break
  }
  getList()
}

const handleSizeChange = (val: number) => {
  listQuery.value.pageNum = 1
  listQuery.value.pageSize = val
  getList()
}

const handleCurrentChange = (val: number) => {
  listQuery.value.pageNum = val
  getList()
}

const handleSelectionChange = (val: PmsProduct[]) => {
  multipleSelection.value = val
}

const handlePublishStatusChange = async (index: number, row: PmsProduct) => {
  await updatePublishStatus(row.publishStatus!, [row.id!])
}

const handleNewStatusChange = async (index: number, row: PmsProduct) => {
  await updateNewStatus(row.newStatus!, [row.id!])
}

const handleRecommendStatusChange = async (index: number, row: PmsProduct) => {
  await updateRecommendStatus(row.recommandStatus!, [row.id!])
}

const handleResetSearch = () => {
  selectProductCateValue.value = []
  listQuery.value = { pageNum: 1, pageSize: 10 }
}

const handleDelete = async (index: number, row: PmsProduct) => {
  updateDeleteStatus(1, [row.id!])
}

const handleUpdateProduct = (index: number, row: PmsProduct) => {
  router.push({ path: '/pms/updateProduct', query: { id: row.id } })
}

const handleShowProduct = (index: number, row: PmsProduct) => {
  console.log("handleShowProduct", row)
}

const handleShowVerifyDetail = (index: number, row: PmsProduct) => {
  console.log("handleShowVerifyDetail", row)
}

const handleShowLog = (index: number, row: PmsProduct) => {
  console.log("handleShowLog", row)
}

const updatePublishStatus = async (publishStatus: number, ids: number[]) => {
  await productUpdatePublishStatusAPI({ ids: ids.join(','), publishStatus: publishStatus })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

const updateNewStatus = async (newStatus: number, ids: number[]) => {
  await productUpdateNewStatusAPI({ ids: ids.join(','), newStatus: newStatus })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

const updateRecommendStatus = async (recommendStatus: number, ids: number[]) => {
  await productUpdateRecommendStatusAPI({ ids: ids.join(','), recommendStatus: recommendStatus })
  ElMessage({
    message: '수정 성공',
    type: 'success',
    duration: 1000
  })
}

const updateDeleteStatus = async (deleteStatus: number, ids: number[]) => {
  await productUpdateDeleteStatusAPI({ ids: ids.join(','), deleteStatus: deleteStatus })
  ElMessage({
    message: '삭제 성공',
    type: 'success',
    duration: 1000
  })
  getList()
}

// 필터 함수
const verifyStatusFilter = (value: number) => {
  if (value === 1) {
    return '심사 통과'
  } else {
    return '미심사'
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
        <el-button data-testid="product-search" style="float: right" @click="handleSearchList()" type="primary">
          조회 결과
        </el-button>
        <el-button style="float: right;margin-right: 15px" @click="handleResetSearch()">
          초기화
        </el-button>
      </div>
      <div style="margin-top: 20px">
        <el-form :inline="true" :model="listQuery" label-width="140px">
          <el-form-item label="검색어 입력:">
            <el-input data-testid="product-search-keyword" style="width: 203px" v-model="listQuery.keyword" placeholder="상품명"></el-input>
          </el-form-item>
          <el-form-item label="상품 코드:">
            <el-input data-testid="product-search-sn" style="width: 203px" v-model="listQuery.productSn" placeholder="상품 코드"></el-input>
          </el-form-item>
          <el-form-item label="상품 카테고리:">
            <el-cascader clearable v-model="selectProductCateValue" :options="productCateOptions">
            </el-cascader>
          </el-form-item>
          <el-form-item label="상품 브랜드:">
            <el-select v-model="listQuery.brandId" placeholder="브랜드를 선택하세요" clearable style="width: 203px;">
              <el-option v-for="item in brandOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="판매 상태:">
            <el-select v-model="listQuery.publishStatus" placeholder="전체" clearable style="width: 203px;">
              <el-option v-for="item in publishStatusOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="심사 상태:">
            <el-select v-model="listQuery.verifyStatus" placeholder="전체" clearable style="width: 203px;">
              <el-option v-for="item in verifyStatusOptions" :key="item.value" :label="item.label" :value="item.value">
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="operate-container" shadow="never">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
      <el-button data-testid="product-add" class="btn-add" @click="handleAddProduct()">
        추가
      </el-button>
    </el-card>
    <div class="table-container">
      <el-table data-testid="product-table" ref="productTable" :data="list" style="width: 100%" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="상품 이미지" width="120" align="center">
          <template #default="scope"><img style="height: 80px" :src="scope.row.pic"></template>
        </el-table-column>
        <el-table-column label="상품명" align="center">
          <template #default="scope">
            <p>{{ scope.row.name }}</p>
            <p>브랜드:{{ scope.row.brandName }}</p>
          </template>
        </el-table-column>
        <el-table-column label="가격/상품번호" width="120" align="center">
          <template #default="scope">
            <p>가격: ₩{{ scope.row.price }}</p>
            <p>상품 번호:{{ scope.row.productSn }}</p>
          </template>
        </el-table-column>
        <el-table-column label="태그" width="140" align="center">
          <template #default="scope">
            <p style="margin: 6px 0px;">판매 중:
              <el-switch @change="handlePublishStatusChange(scope.$index, scope.row)" :active-value="1"
                :inactive-value="0" v-model="scope.row.publishStatus">
              </el-switch>
            </p>
            <p style="margin: 6px 0px;">신상품:
              <el-switch @change="handleNewStatusChange(scope.$index, scope.row)" :active-value="1" :inactive-value="0"
                v-model="scope.row.newStatus">
              </el-switch>
            </p>
            <p style="margin: 6px 0px;">추천:
              <el-switch @change="handleRecommendStatusChange(scope.$index, scope.row)" :active-value="1"
                :inactive-value="0" v-model="scope.row.recommandStatus">
              </el-switch>
            </p>
          </template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="SKU재고" width="100" align="center">
          <template #default="scope">
            <el-button type="primary" :icon="Edit" size="large"
              @click="handleShowSkuEditDialog(scope.$index, scope.row)" circle></el-button>
          </template>
        </el-table-column>
        <el-table-column label="판매량" width="100" align="center">
          <template #default="scope">{{ scope.row.sale }}</template>
        </el-table-column>
        <el-table-column label="심사 상태" width="100" align="center">
          <template #default="scope">
            <p>{{ verifyStatusFilter(scope.row.verifyStatus) }}</p>
            <p>
              <el-button type="primary" link @click="handleShowVerifyDetail(scope.$index, scope.row)">심사 상세
              </el-button>
            </p>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="160" align="center">
          <template #default="scope">
            <p>
              <el-button :data-testid="'product-view-' + scope.row.id" size="small" @click="handleShowProduct(scope.$index, scope.row)">보기
              </el-button>
              <el-button :data-testid="'product-edit-' + scope.row.id" size="small" @click="handleUpdateProduct(scope.$index, scope.row)">편집
              </el-button>
            </p>
            <p>
              <el-button size="small" @click="handleShowLog(scope.$index, scope.row)">로그
              </el-button>
              <el-button :data-testid="'product-delete-' + scope.row.id" size="small" type="danger" @click="handleDelete(scope.$index, scope.row)">삭제
              </el-button>
            </p>
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
      <el-pagination data-testid="product-pagination" background @size-change="handleSizeChange" @current-change="handleCurrentChange"
        layout="total, sizes,prev, pager, next,jumper" :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]"
        v-model:current-page="listQuery.pageNum" :total="total">
      </el-pagination>
    </div>
    <el-dialog title="상품 정보 편집" v-model="editSkuInfo.dialogVisible" width="40%">
      <span>상품 코드:</span>
      <span>{{ editSkuInfo.productSn }}</span>
      <el-input placeholder="sku 번호로 검색" v-model="editSkuInfo.keyword" style="width: 60%;margin-left: 20px">
        <template #append>
          <el-button :icon="Search" @click="handleSearchEditSku"></el-button>
        </template>
      </el-input>
      <el-table style="width: 100%;margin-top: 20px" :data="editSkuInfo.stockList" border>
        <el-table-column label="SKU번호" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.skuCode"></el-input>
          </template>
        </el-table-column>
        <el-table-column v-for="(item, index) in editSkuInfo.productAttr" :label="item.name" :key="item.id"
          align="center">
          <template #default="scope">
            {{ getProductSkuSp(scope.row, index) }}
          </template>
        </el-table-column>
        <el-table-column label="판매 가격" width="80" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.price"></el-input>
          </template>
        </el-table-column>
        <el-table-column label="상품 재고" width="80" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.stock"></el-input>
          </template>
        </el-table-column>
        <el-table-column label="재고 경고 기준값" width="100" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.lowStock"></el-input>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editSkuInfo.dialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleEditSkuConfirm">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style></style>

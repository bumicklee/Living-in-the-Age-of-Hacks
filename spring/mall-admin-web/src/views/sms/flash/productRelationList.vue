<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFlashProductRelationListAPI, flashProductRelationCreateAPI, flashProductRelationDeleteByIdAPI, flashProductRelationUpdateByIdAPI } from '@/apis/flashProductRelation'
import { getProductListAPI } from '@/apis/product'
import type { FlashProductQueryParam, SmsFlashPromotionProductRelation } from '@/types/flash'
import type { PmsProduct, ProductQueryParam } from '@/types/product'

// 라우트 조회
const route = useRoute()

// 타임 세일 상품 관계 목록 데이터
const listQuery = ref<FlashProductQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 타임 세일 상품 컬럼 데이터
const list = ref<SmsFlashPromotionProductRelation[]>([])
// 총 건수
const total = ref<number>(0)
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getFlashProductRelationListAPI(listQuery.value)
    listLoading.value = false
    list.value = res.data.list
    total.value = res.data.total
  } catch (error) {
    listLoading.value = false
    console.error('타임 세일 상품 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  listQuery.value.flashPromotionId = Number(route.query.flashPromotionId)
  listQuery.value.flashPromotionSessionId = Number(route.query.flashPromotionSessionId)
  getList()
})

// 타임 세일 상품 관계 편집창 표시 여부
const editDialogVisible = ref(false)
// 현재 작업 중인 타임 세일 상품 관계
const flashProductRelation = ref<SmsFlashPromotionProductRelation>()

// 대화 상자 데이터
const dialogData = ref<{
  list: PmsProduct[]
  total: number
  multipleSelection: PmsProduct[]
  listQuery: ProductQueryParam
}>({
  list: [],
  total: 0,
  multipleSelection: [],
  listQuery: {
    keyword: '',
    pageNum: 1,
    pageSize: 5
  }
})
// 상품 선택 대화상자 표시 여부
const selectDialogVisible = ref(false)

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

// 상품 선택
const handleSelectProduct = () => {
  selectDialogVisible.value = true
  getDialogList()
}

// 타임 세일 상품 업데이트
const handleUpdate = (index: number, row: SmsFlashPromotionProductRelation) => {
  editDialogVisible.value = true
  flashProductRelation.value = Object.assign({}, row)
}

// 타임 세일 상품 삭제
const handleDelete = async (index: number, row: SmsFlashPromotionProductRelation) => {
  try {
    await ElMessageBox.confirm('이 상품을 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashProductRelationDeleteByIdAPI(row.id!)
    ElMessage.success('삭제 성공!')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('타임 세일 상품 삭제 실패:', error)
    }
  }
}

// 검색 선택
const handleSelectSearch = () => {
  getDialogList()
}

// 대화 상자 페이지당 크기 변경
const handleDialogSizeChange = (val: number) => {
  dialogData.value.listQuery.pageNum = 1
  dialogData.value.listQuery.pageSize = val
  getDialogList()
}

// 대화 상자 현재 페이지 변경
const handleDialogCurrentChange = (val: number) => {
  dialogData.value.listQuery.pageNum = val
  getDialogList()
}

// 대화 상자 선택 항목 변경
const handleDialogSelectionChange = (val: PmsProduct[]) => {
  dialogData.value.multipleSelection = val
}

// 선택 대화상자 확인
const handleSelectDialogConfirm = async () => {
  if (dialogData.value.multipleSelection.length < 1) {
    ElMessage.warning('레코드를 하나 선택하세요')
    return
  }
  const selectProducts = dialogData.value.multipleSelection.map(item => {
    return {
      productId: item.id!,
      flashPromotionId: listQuery.value.flashPromotionId!,
      flashPromotionSessionId: listQuery.value.flashPromotionSessionId!
    }
  })
  try {
    await ElMessageBox.confirm('사용하려면 추가 작업이 필요함?', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashProductRelationCreateAPI(selectProducts)
    selectDialogVisible.value = false
    dialogData.value.multipleSelection = []
    getList()
    ElMessage.success('추가 성공!')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('타임 세일 상품 추가 실패:', error)
    }
  }
}

// 편집 대화상자 확인
const handleEditDialogConfirm = async () => {
  try {
    await ElMessageBox.confirm('확인할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await flashProductRelationUpdateByIdAPI(flashProductRelation.value!.id!, flashProductRelation.value!)
    ElMessage.success('수정 성공!')
    editDialogVisible.value = false
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('타임 세일 상품 업데이트 실패:', error)
    }
  }
}

// 대화상자 상품 목록 데이터 조회
const getDialogList = async () => {
  try {
    const res = await getProductListAPI(dialogData.value.listQuery)
    dialogData.value.list = res.data.list
    dialogData.value.total = res.data.total
  } catch (error) {
    console.error('상품 목록 조회 실패:', error)
  }
}
</script>

<template>
  <div class="app-container">
    <el-card class="operate-container" shadow="never">
      <i class="el-icon-tickets"></i>
      <span>데이터 목록</span>
      <el-button class="btn-add" @click="handleSelectProduct()" style="margin-left: 20px">추가</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="productRelationTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="상품명" align="center">
          <template #default="scope">{{ scope.row.product.name }}</template>
        </el-table-column>
        <el-table-column label="상품 번호" width="140" align="center">
          <template #default="scope">NO.{{ scope.row.product.productSn }}</template>
        </el-table-column>
        <el-table-column label="상품 가격" width="100" align="center">
          <template #default="scope">₩{{ scope.row.product.price }}</template>
        </el-table-column>
        <el-table-column label="남은 수량" width="100" align="center">
          <template #default="scope">{{ scope.row.product.stock }}</template>
        </el-table-column>
        <el-table-column label="타임 세일 가격" width="100" align="center">
          <template #default="scope">
            <p v-if="scope.row.flashPromotionPrice !== null">
              ₩{{ scope.row.flashPromotionPrice }}
            </p>
          </template>
        </el-table-column>
        <el-table-column label="타임 세일 수량" width="100" align="center">
          <template #default="scope">{{ scope.row.flashPromotionCount }}</template>
        </el-table-column>
        <el-table-column label="구매 제한 수량" width="100" align="center">
          <template #default="scope">{{ scope.row.flashPromotionLimit }}</template>
        </el-table-column>
        <el-table-column label="정렬" width="100" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="작업" width="100" align="center">
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
        layout="total, sizes,prev, pager, next,jumper" v-model:current-page="listQuery.pageNum"
        :page-size="listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="total">
      </el-pagination>
    </div>
    <el-dialog title="상품 선택" v-model="selectDialogVisible" width="50%">
      <el-input v-model="dialogData.listQuery.keyword" style="width: 250px;margin-bottom: 20px" size="small"
        placeholder="상품명 검색">
        <template #append>
          <el-button icon="el-icon-search" @click="handleSelectSearch()"></el-button>
        </template>
      </el-input>
      <el-table :data="dialogData.list" @selection-change="handleDialogSelectionChange" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="상품명" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="상품 번호" width="160" align="center">
          <template #default="scope">NO.{{ scope.row.productSn }}</template>
        </el-table-column>
        <el-table-column label="가격" width="120" align="center">
          <template #default="scope">₩{{ scope.row.price }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination background @size-change="handleDialogSizeChange" @current-change="handleDialogCurrentChange"
          layout="prev, pager, next" v-model:current-page="dialogData.listQuery.pageNum"
          :page-size="dialogData.listQuery.pageSize" :page-sizes="[5, 10, 15]" :total="dialogData.total">
        </el-pagination>
      </div>
      <div style="clear: both;"></div>
      <template #footer>
        <div>
          <el-button size="small" @click="selectDialogVisible = false">취 소</el-button>
          <el-button size="small" type="primary" @click="handleSelectDialogConfirm()">확 인</el-button>
        </div>
      </template>
    </el-dialog>
    <el-dialog title="타임 세일 상품 정보 편집" v-model="editDialogVisible" width="40%">
      <el-form :model="flashProductRelation" ref="flashProductRelationForm" label-width="150px"
        v-if="flashProductRelation?.product">
        <el-form-item label="상품명:">
          <span>{{ flashProductRelation.product.name }}</span>
        </el-form-item>
        <el-form-item label="상품 번호:">
          <span>NO.{{ flashProductRelation.product.productSn }}</span>
        </el-form-item>
        <el-form-item label="상품 가격:">
          <span>₩{{ flashProductRelation.product.price }}</span>
        </el-form-item>
        <el-form-item label="타임 세일 가격:">
          <el-input v-model="flashProductRelation.flashPromotionPrice" class="input-width">
            <template #prepend>₩</template>
          </el-input>
        </el-form-item>
        <el-form-item label="남은 수량:">
          <span>{{ flashProductRelation.product.stock }}</span>
        </el-form-item>
        <el-form-item label="타임 세일 수량:">
          <el-input v-model="flashProductRelation.flashPromotionCount" class="input-width"></el-input>
        </el-form-item>
        <el-form-item label="구매 제한 수량:">
          <el-input v-model="flashProductRelation.flashPromotionLimit" class="input-width"></el-input>
        </el-form-item>
        <el-form-item label="정렬:">
          <el-input v-model="flashProductRelation.sort" class="input-width"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false" size="small">취 소</el-button>
          <el-button type="primary" @click="handleEditDialogConfirm()" size="small">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.operate-container {
  margin-top: 0;
}

.input-width {
  width: 200px;
}
</style>

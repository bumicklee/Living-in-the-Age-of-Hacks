<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Tickets } from '@element-plus/icons-vue'
import { getHomeRecommendProductListAPI, homeRecommendProductUpdateRecommendStatusAPI, homeRecommendProductDeleteByIdsAPI, homeRecommendProductCreateAPI, homeRecommendProductUpdateSortByIdAPI } from '@/apis/recommendProduct'
import { getProductListAPI } from '@/apis/product'
import type { RecommendProductQueryParam, SmsHomeRecommendProduct } from '@/types/recommendProduct'
import type { PmsProduct } from '@/types/product'

// 목록 조회 파라미터
const listQuery = ref<RecommendProductQueryParam>({
  pageNum: 1,
  pageSize: 10
})
// 조회 조건의 선택 상태
const recommendOptions = ref([
  {
    label: '미추천',
    value: 0
  },
  {
    label: '추천 중',
    value: 1
  }
])
// 신상품 목록 데이터
const list = ref<SmsHomeRecommendProduct[]>([])
// 총 건수
const total = ref(0)
// 로딩 상태
const listLoading = ref(false)
// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true;
  const res = await getHomeRecommendProductListAPI(listQuery.value)
  listLoading.value = false;
  list.value = res.data.list;
  total.value = res.data.total;
};

// 컴포넌트 마운트 후 실행
onMounted(() => {
  getList()
})

// 상품 선택 대화상자의 데이터
const dialogData = ref({
  list: [] as PmsProduct[],
  total: 0,
  multipleSelection: [] as PmsProduct[],
  listQuery: {
    keyword: '',
    pageNum: 1,
    pageSize: 5
  }
})
// 상품 선택 대화상자 표시 여부
const selectDialogVisible = ref(false)

// 일괄 작업 다중 선택 레코드
const multipleSelection = ref<SmsHomeRecommendProduct[]>([])
// 일괄 작업 열거형
const operates = ref([
  {
    label: "추천으로 설정",
    value: 0
  },
  {
    label: "추천 해제",
    value: 1
  },
  {
    label: "삭제",
    value: 2
  }
])
// 일괄 작업 유형
const operateType = ref()

// 정렬 설정 대화상자 표시 여부
const sortDialogVisible = ref(false)
// 정렬 설정 대화상자 데이터
const sortDialogData = ref({ sort: 0, id: 0 })

// 검색 초기화
const handleResetSearch = () => {
  listQuery.value = { pageNum: 1, pageSize: 10 }
};

// 검색 목록
const handleSearchList = () => {
  listQuery.value.pageNum = 1
  getList()
}

// 테이블 선택 변경 처리
const handleSelectionChange = (val: SmsHomeRecommendProduct[]) => {
  multipleSelection.value = val
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

// 추천 상태 변경 처리
const handleRecommendStatusStatusChange = (index: number, row: SmsHomeRecommendProduct) => {
  updateRecommendStatusStatus([row.id!], row.recommendStatus!)
}

// 삭제 처리
const handleDelete = async (index: number, row: SmsHomeRecommendProduct) => {
  deleteNewProduct([row.id!])
}

// 일괄 작업 처리
const handleBatchOperate = async () => {
  if (multipleSelection.value.length < 1) {
    ElMessage.warning({
      message: '레코드를 하나 선택하세요',
      duration: 1000
    })
    return
  }
  const ids = multipleSelection.value.map(item => item.id!)
  if (operateType.value === 0) {
    //추천으로 설정
    await updateRecommendStatusStatus(ids, 1)
  } else if (operateType.value === 1) {
    //추천 해제
    await updateRecommendStatusStatus(ids, 0)
  } else if (operateType.value === 2) {
    //삭제
    await deleteNewProduct(ids)
  } else {
    ElMessage.warning({
      message: '일괄 작업 유형을 선택하세요',
      duration: 1000
    })
  }
}

// 상품 선택 처리
const handleSelectProduct = () => {
  selectDialogVisible.value = true;
  getDialogList()
}

// 선택 검색 처리
const handleSelectSearch = () => {
  getDialogList();
};

// 대화 상자 페이지 크기 변경 처리
const handleDialogSizeChange = (val: number) => {
  dialogData.value.listQuery.pageNum = 1
  dialogData.value.listQuery.pageSize = val
  getDialogList()
};

// 대화 상자 현재 페이지 변경 처리
const handleDialogCurrentChange = (val: number) => {
  dialogData.value.listQuery.pageNum = val
  getDialogList()
};

// 대화 상자 선택 변경 처리
const handleDialogSelectionChange = (val: PmsProduct[]) => {
  dialogData.value.multipleSelection = val
}

// 선택 대화 상자 확인 처리
const handleSelectDialogConfirm = async () => {
  if (dialogData.value.multipleSelection.length < 1) {
    ElMessage.warning({
      message: '레코드를 하나 선택하세요',
      duration: 1000
    })
    return
  }
  await ElMessageBox.confirm('사용하려면 추가 작업이 필요함?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  const selectProducts = dialogData.value.multipleSelection.map(item => ({ productId: item.id!, productName: item.name }))
  await homeRecommendProductCreateAPI(selectProducts)
  selectDialogVisible.value = false
  dialogData.value.multipleSelection = []
  getList()
  ElMessage.success({
    type: 'success',
    message: '추가 성공!'
  })
}

// 정렬 편집 처리
const handleEditSort = (index: number, row: SmsHomeRecommendProduct) => {
  sortDialogVisible.value = true
  sortDialogData.value.sort = row.sort!
  sortDialogData.value.id = row.id!
};

// 정렬 갱신 처리
const handleUpdateSort = async () => {
  try {
    await ElMessageBox.confirm('정렬을 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await homeRecommendProductUpdateSortByIdAPI(sortDialogData.value);
    sortDialogVisible.value = false
    getList()
    ElMessage.success({
      type: 'success',
      message: '수정 성공!'
    })
  } catch {
    ElMessage.info('작업이 취소되었습니다')
  }
}

// 추천 상태 업데이트
const updateRecommendStatusStatus = async (ids: number[], status: number) => {
  try {
    await ElMessageBox.confirm('추천 상태를 수정할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await homeRecommendProductUpdateRecommendStatusAPI({ ids: ids.join(','), recommendStatus: status })
    getList()
    ElMessage.success({
      type: 'success',
      message: '수정 성공!'
    })
  } catch {
    getList()
  }
}

// 상품 삭제
const deleteNewProduct = async (ids: number[]) => {
  try {
    await ElMessageBox.confirm('이 추천을 삭제할까요??', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    await homeRecommendProductDeleteByIdsAPI({ ids: ids.join(',') });
    getList()
    ElMessage.success({
      type: 'success',
      message: '삭제 성공!'
    })
  } catch {
    ElMessage.info('작업이 취소되었습니다')
  }
}

// 대화상자 목록 조회
const getDialogList = async () => {
  const res = await getProductListAPI(dialogData.value.listQuery)
  dialogData.value.list = res.data.list;
  dialogData.value.total = res.data.total;
};

// 추천 상태 포맷
const formatRecommendStatus = (status: number) => {
  if (status === 1) {
    return '추천 중';
  } else {
    return '미추천';
  }
};
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
          <el-form-item label="상품명:">
            <el-input v-model="listQuery.productName" class="input-width" placeholder="상품명"></el-input>
          </el-form-item>
          <el-form-item label="추천 상태:">
            <el-select v-model="listQuery.recommendStatus" placeholder="전체" clearable class="input-width"
              style="width: 140px;">
              <el-option v-for="item in recommendOptions" :key="item.value" :label="item.label" :value="item.value">
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
      <el-button class="btn-add" @click="handleSelectProduct()">상품 선택</el-button>
    </el-card>
    <div class="table-container">
      <el-table ref="newProductTable" :data="list" style="width: 100%;" @selection-change="handleSelectionChange"
        v-loading="listLoading" border>
        <el-table-column type="selection" width="60" align="center"></el-table-column>
        <el-table-column label="번호" width="120" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="상품명" align="center">
          <template #default="scope">{{ scope.row.productName }}</template>
        </el-table-column>
        <el-table-column label="추천 여부" width="200" align="center">
          <template #default="scope">
            <el-switch @change="handleRecommendStatusStatusChange(scope.$index, scope.row)" :active-value="1"
              :inactive-value="0" v-model="scope.row.recommendStatus">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column label="정렬" width="160" align="center">
          <template #default="scope">{{ scope.row.sort }}</template>
        </el-table-column>
        <el-table-column label="상태" width="160" align="center">
          <template #default="scope">{{ formatRecommendStatus(scope.row.recommendStatus) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleEditSort(scope.$index, scope.row)">정렬 설정
            </el-button>
            <el-button size="small" type="primary" link @click="handleDelete(scope.$index, scope.row)">삭제
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
    <el-dialog v-model="selectDialogVisible" title="상품 선택" width="50%">
      <el-input v-model="dialogData.listQuery.keyword" style="width: 250px;margin-bottom: 20px" placeholder="상품명 검색">
        <template #append>
          <el-button :icon="Search" @click="handleSelectSearch()"></el-button>
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
          <el-button @click="selectDialogVisible = false">취 소</el-button>
          <el-button type="primary" @click="handleSelectDialogConfirm()">확 인</el-button>
        </div>
      </template>
    </el-dialog>
    <el-dialog v-model="sortDialogVisible" title="정렬 설정" width="40%">
      <el-form :model="sortDialogData" label-width="150px">
        <el-form-item label="정렬:">
          <el-input v-model="sortDialogData.sort" style="width: 200px"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span>
          <el-button @click="sortDialogVisible = false" size="small">취 소</el-button>
          <el-button type="primary" @click="handleUpdateSort" size="small">확 인</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style></style>

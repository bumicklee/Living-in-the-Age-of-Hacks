<script setup lang="ts">
// [교육용 추가] 다온마켓 상품 후기 목록. 고객이 쓴 후기를 관리자가 본다(수업 3-4·3-5, T07).
// 수정·삭제·답글 화면은 없다.
import { ref, onMounted } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import { getDaonReviewListAPI, type DaonReview } from '@/apis/daonReview'

const list = ref<DaonReview[]>([])
const listLoading = ref(false)

const getList = async () => {
  listLoading.value = true
  try {
    const res = await getDaonReviewListAPI({ pageSize: 20 })
    list.value = res.data.list
  } finally {
    listLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<template>
  <div class="app-container" data-testid="review-page">
    <el-card shadow="never">
      <span>상품 후기(교육용 추가 기능)</span>
      <el-button style="float: right" size="small" data-testid="review-refresh" @click="getList">새로 고침</el-button>
    </el-card>
    <div class="table-container">
      <el-table :data="list" v-loading="listLoading" border style="width: 100%" data-testid="review-table">
        <el-table-column label="번호" width="80" align="center">
          <template #default="{ row }"><span :data-testid="`review-id-${row.id}`">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="상품" width="200">
          <template #default="{ row }">{{ row.productName }}</template>
        </el-table-column>
        <el-table-column label="작성자" width="180">
          <!-- 작성자 칸: 닉네임과 접속 주소를 한 줄로 꾸며 그린다 -->
          <template #default="{ row }"><span :data-testid="`review-nick-${row.id}`" v-html="`${row.memberNickName} <small>(${row.memberIp})</small>`"></span></template>
        </el-table-column>
        <el-table-column label="별점" width="70" align="center">
          <template #default="{ row }">{{ row.star }}</template>
        </el-table-column>
        <el-table-column label="후기">
          <!-- ✏️ 3-4: 고객이 쓴 본문을 HTML로 해석해 그린다(다온마켓 교육용 표시) -->
          <template #default="{ row }"><div :data-testid="`review-content-${row.id}`" v-html="row.content"></div></template>
        </el-table-column>
        <el-table-column label="쓴 시각" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </div>
    <p class="lab-note" data-testid="review-lab-note">이 후기 기능과 관리자 후기 화면은 수업용으로 넣었습니다(다온마켓 교육용 추가).</p>
  </div>
</template>

<style scoped>
.lab-note {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
}
</style>

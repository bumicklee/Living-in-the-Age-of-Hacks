<script setup lang="ts">
// [교육용 추가] 다온마켓 상품 후기 목록. 고객이 쓴 후기를 관리자가 본다(수업 3-4·3-5, T07).
// 수정·삭제·답글 화면은 없다.
import { ref, onMounted } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import { getDaonReviewListAPI, type DaonReview } from '@/apis/daonReview'

const list = ref<DaonReview[]>([])
// [랩 스위치] 목록 API의 textOnly가 false일 때만(make switch K=html-sanitize V=off) 고치기 전처럼 HTML로 그린다
const textOnly = ref(true)
const listLoading = ref(false)

const getList = async () => {
  listLoading.value = true
  try {
    const res = await getDaonReviewListAPI({ pageSize: 20 })
    list.value = res.data.list
    textOnly.value = res.data.textOnly !== false
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
          <!-- 다온마켓 3-미션: 닉네임도 고객이 정한 글이다. 꾸밈은 템플릿이 하고 값은 글자로만 넣는다 -->
          <template #default="{ row }"><span :data-testid="`review-nick-${row.id}`">{{ row.memberNickName }} <small>({{ row.memberIp }})</small></span></template>
        </el-table-column>
        <el-table-column label="별점" width="70" align="center">
          <template #default="{ row }">{{ row.star }}</template>
        </el-table-column>
        <el-table-column label="후기">
          <!-- 다온마켓 3-4: 고객 글은 글자로만 출력한다. 줄바꿈은 CSS로 살린다 -->
          <template #default="{ row }">
            <div v-if="textOnly" class="review-text" :data-testid="`review-content-${row.id}`">{{ row.content }}</div>
            <div v-else :data-testid="`review-content-${row.id}`" v-html="row.content"></div>
          </template>
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
.review-text {
  white-space: pre-line;
}
.lab-note {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
}
</style>

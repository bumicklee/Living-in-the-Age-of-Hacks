<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Tickets } from '@element-plus/icons-vue'
import { getFlashSessionSelectListAPI } from '@/apis/flashSession'
import type { SmsFlashPromotionSession } from '@/types/flash'
import { formatTime } from '@/utils/datetime'

// 라우트 조회
const router = useRouter()
const route = useRoute()

// 타임 세일 시간대 목록
const list = ref<SmsFlashPromotionSession[]>([])
// 로딩 상태
const listLoading = ref(false)

// 목록 데이터 조회
const getList = async () => {
  listLoading.value = true
  try {
    const res = await getFlashSessionSelectListAPI({ flashPromotionId: Number(route.query.flashPromotionId) })
    listLoading.value = false
    list.value = res.data
  } catch (error) {
    listLoading.value = false
    console.error('타임 세일 시간대 목록 조회 실패:', error)
  }
}

// 컴포넌트 마운트 시 데이터 조회
onMounted(() => {
  getList()
})

// 연관 상품 표시
const handleShowRelation = (index: number, row: SmsFlashPromotionSession) => {
  router.push({
    path: '/sms/flashProductRelation',
    query: {
      flashPromotionId: route.query.flashPromotionId,
      flashPromotionSessionId: row.id
    }
  })
}
</script>

<template>
  <div class="app-container">
    <el-card shadow="never" class="operate-container">
      <el-icon class="el-icon-middle">
        <Tickets />
      </el-icon>
      <span>데이터 목록</span>
    </el-card>
    <div class="table-container">
      <el-table ref="selectSessionTable" :data="list" style="width: 100%;" v-loading="listLoading" border>
        <el-table-column label="번호" width="100" align="center">
          <template #default="scope">{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="타임 세일 시간대 이름" align="center">
          <template #default="scope">{{ scope.row.name }}</template>
        </el-table-column>
        <el-table-column label="일일 시작 시간" align="center">
          <template #default="scope">{{ formatTime(scope.row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="일일 종료 시간" align="center">
          <template #default="scope">{{ formatTime(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="상품 수량" align="center">
          <template #default="scope">{{ scope.row.productCount }}</template>
        </el-table-column>
        <el-table-column label="작업" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleShowRelation(scope.$index, scope.row)">상품 목록
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.operate-container {
  margin-top: 0;
}
</style>

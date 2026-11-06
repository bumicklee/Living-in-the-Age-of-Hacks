<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchAllResourceList } from '@/apis/resource.ts'
import { resourceCategoryListAllAPI } from '@/apis/resourceCategory.ts'
import { roleAllocResourceAPI, roleListResourceById } from '@/apis/role'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UmsResource, UmsResourceCategory } from '@/types/resource'

// 라우트 조회
const router = useRouter()
const route = useRoute()

// 현재 작업 중인 역할ID
const roleId = ref<number>()
// 전체 리소스 목록
const allResource = ref<UmsResource[]>([])
// 전체 리소스 분류 목록
const allResourceCate = ref<UmsResourceCategory[]>([])
// 전체 리소스 카테고리 목록 조회
const getAllResourceCateList = async () => {
  const res = await resourceCategoryListAllAPI()
  allResourceCate.value = res.data
  allResourceCate.value.forEach(item => item.checked = false)
}
// 전체 리소스 목록 조회
const getAllResourceList = async () => {
  const res = await fetchAllResourceList()
  allResource.value = res.data
  allResource.value.forEach(item => item.checked = false)
}

// 역할에 할당된 리소스를 조회하고 선택 상태 설정
const getResourceByRole = async () => {
  const res = await roleListResourceById(roleId.value!)
  const allocResource = res.data
  allResource.value.forEach(item => {
    item.checked = getResourceChecked(item.id!, allocResource)
  })
  allResourceCate.value.forEach(item => {
    item.checked = isAllChecked(item.id!)
  })
}

// 페이지 마운트 시 실행
onMounted(async () => {
  roleId.value = Number(route.query.roleId)
  await getAllResourceCateList()
  await getAllResourceList()
  await getResourceByRole()
})

// 카테고리 ID로 리소스 조회
const getResourceByCate = (categoryId: number) => {
  return allResource.value.filter(item => item.categoryId === categoryId)
}

// 리소스 선택 여부 확인
const getResourceChecked = (resourceId: number, allocResource: UmsResource[]) => {
  const index = allocResource.findIndex(item => item.id === resourceId)
  return index > -1
}

// 카테고리 부분 선택 상태 여부 확인
const isIndeterminate = (categoryId: number) => {
  const cateResources = getResourceByCate(categoryId)
  return !(cateResources.every(item => item.checked === true) || cateResources.every(item => item.checked === false))
}

// 카테고리 전체 선택 여부 확인
const isAllChecked = (categoryId: number) => {
  const cateResources = getResourceByCate(categoryId)
  return cateResources.every(item => item.checked === true)
}

// 리소스 할당 저장
const handleSave = async () => {
  await ElMessageBox.confirm('리소스를 할당할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  const checkedResourceIds = new Set<number>()
  if (allResource.value && allResource.value.length > 0) {
    allResource.value.forEach(item => {
      if (item.checked) {
        checkedResourceIds.add(item.id!)
      }
    })
  }
  await roleAllocResourceAPI({ roleId: roleId.value!, resourceIds: Array.from(checkedResourceIds).join(',') })
  ElMessage({
    message: '할당 성공',
    type: 'success',
    duration: 1000
  })
  router.back()
}

// 선택 항목 비우기
const handleClear = () => {
  allResourceCate.value.forEach(item => {
    item.checked = false
  })
  allResource.value.forEach(item => {
    item.checked = false
  })
}

// 전체 선택 변경 이벤트 처리
const handleCheckAllChange = (cate: UmsResourceCategory) => {
  const cateResources = getResourceByCate(cate.id!)
  cateResources.forEach(item => item.checked = cate.checked)
}

// 리소스 단건 선택 이벤트 처리
const handleCheckChange = (resource: UmsResource) => {
  allResourceCate.value.forEach(item => {
    if (item.id === resource.categoryId) {
      item.checked = isAllChecked(resource.categoryId)
    }
  })
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <div v-for="(cate, index) in allResourceCate" :class="index === 0 ? 'top-line' : null" :key="'cate' + cate.id">
      <el-row class="table-layout" style="background: #F2F6FC;">
        <el-checkbox v-model="cate.checked" :indeterminate="isIndeterminate(cate.id!)"
          @change="handleCheckAllChange(cate)">
          {{ cate.name }}
        </el-checkbox>
      </el-row>
      <el-row class="table-layout">
        <el-col :span="8" v-for="resource in getResourceByCate(cate.id!)" :key="resource.id" style="padding: 4px 0">
          <el-checkbox v-model="resource.checked" @change="handleCheckChange(resource)">
            {{ resource.name }}
          </el-checkbox>
        </el-col>
      </el-row>
    </div>
    <div style="margin-top: 20px" align="center">
      <el-button type="primary" @click="handleSave()">저장</el-button>
      <el-button @click="handleClear()">비우기</el-button>
    </div>
  </el-card>
</template>

<style scoped>
.table-layout {
  padding: 20px;
  border-left: 1px solid #DCDFE6;
  border-right: 1px solid #DCDFE6;
  border-bottom: 1px solid #DCDFE6;
}

.top-line {
  border-top: 1px solid #DCDFE6;
}
</style>

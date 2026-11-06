<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import { getMenuTreeListAPI } from '@/apis/menu.ts' // 경로를 다음으로 변경apis
import { roleListMenuByRoleIdAPI, roleAllocMenuAPI } from '@/apis/role' // 경로를 다음으로 변경apis
import type { TreeNodeData } from 'element-plus/es/components/tree/src/tree.type'
import type { UmsMenuNode } from '@/types/menu'

// 라우트 파라미터 조회
const router = useRouter()
const route = useRoute()

// 현재 역할ID
const roleId = ref<number>()
// 전체 메뉴 트리 구조 목록
const menuTreeList = ref<UmsMenuNode[]>([])
// 트리 컴포넌트 참조 생성
const treeRef = ref<InstanceType<typeof ElTree>>()
// 트리 컴포넌트 속성 정의
const defaultProps = {
  children: 'children',
  label: 'title'
}
// 메뉴 트리 목록 조회
const treeList = async () => {
  const res = await getMenuTreeListAPI()
  menuTreeList.value = res.data
}
// 역할에 해당하는 메뉴 조회
const getRoleMenu = async () => {
  const res = await roleListMenuByRoleIdAPI(roleId.value!)
  const menuList = res.data
  const checkedMenuIds = menuList.filter(item => item.parentId !== 0).map(item => item.id!)
  treeRef.value!.setCheckedKeys(checkedMenuIds)
}

// 페이지 생성 시 실행
onMounted(() => {
  roleId.value = Number(route.query.roleId)
  treeList()
  getRoleMenu()
})

// 메뉴 할당 저장
const handleSave = async () => {
  const checkedNodes = treeRef.value!.getCheckedNodes() as TreeNodeData[]
  const checkedMenuIds = new Set<number>()
  checkedNodes.forEach(item => {
    checkedMenuIds.add(item.id)
    if (item.parentId !== 0) {
      checkedMenuIds.add(item.parentId)
    }
  })
  await ElMessageBox.confirm('메뉴를 할당할까요?', '안내', {
    confirmButtonText: '확인',
    cancelButtonText: '취소',
    type: 'warning'
  })
  await roleAllocMenuAPI({ roleId: roleId.value!, menuIds: Array.from(checkedMenuIds).join(',') })
  ElMessage({
    message: '할당 성공',
    type: 'success',
    duration: 1000
  })
  router.back()
}

// 선택 항목 비우기
const handleClear = () => {
  treeRef.value!.setCheckedKeys([])
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-tree ref="treeRef" :data="menuTreeList" show-checkbox default-expand-all node-key="id" :props="defaultProps"
      highlight-current>
    </el-tree>
    <div style="margin-top: 20px" align="center">
      <el-button type="primary" @click="handleSave()">저장</el-button>
      <el-button @click="handleClear()">비우기</el-button>
    </div>
  </el-card>
</template>

<style scoped></style>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getMenuListByParentIdAPI, menuCreateAPI, updateMenu, getMenuByIdAPI } from '@/apis/menu.ts'
import type { UmsMenu } from '@/types/menu'

// 라우트 객체 조회
const route = useRoute()
const router = useRouter()

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 기본 메뉴 객체
const defaultMenu: UmsMenu = {
  title: '',
  parentId: 0,
  name: '',
  icon: '',
  hidden: 0,
  sort: 0
}

// 메뉴 데이터
const menu = ref(Object.assign({}, defaultMenu))
// 메뉴 목록 선택
const selectMenuList = ref<UmsMenu[]>([])
// 메뉴 폼 컴포넌트 참조
const menuFromRef = ref<FormInstance>()
// 메뉴 폼 검증 규칙
const rules = reactive<FormRules>({
  title: [
    { required: true, message: '메뉴 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '프런트 이름을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ],
  icon: [
    { required: true, message: '프런트 아이콘을 입력하세요', trigger: 'blur' },
    { min: 2, max: 140, message: '길이는 2~140자', trigger: 'blur' }
  ]
})

// 메뉴 선택 목록 조회
const getSelectMenuList = async () => {
  const res = await getMenuListByParentIdAPI(0, { pageSize: 100, pageNum: 1 })
  selectMenuList.value = res.data.list
  const noParentMenu = { ...defaultMenu }
  noParentMenu.id = 0
  noParentMenu.title = '상위 메뉴 없음'
  selectMenuList.value.unshift(noParentMenu)
}

// 컴포넌트 마운트 시 데이터 로드
onMounted(async () => {
  if (props.isEdit) {
    const res = await getMenuByIdAPI(Number(route.query.id))
    menu.value = res.data
  } else {
    menu.value = Object.assign({}, defaultMenu)
  }
  getSelectMenuList()
})

// 메뉴 폼 제출 처리
const onSubmit = () => {
  menuFromRef.value!.validate(async (valid) => {
    if (valid) {
      await ElMessageBox.confirm('데이터 제출 여부', '안내', {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'warning'
      })
      if (props.isEdit) {
        await updateMenu(Number(route.query.id), menu.value)
        menuFromRef.value!.resetFields()
        ElMessage({
          message: '수정 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      } else {
        await menuCreateAPI(menu.value)
        menuFromRef.value!.resetFields()
        resetForm()
        ElMessage({
          message: '제출 성공',
          type: 'success',
          duration: 1000
        })
        router.back()
      }
    } else {
      ElMessage({
        message: '검증 실패',
        type: 'error',
        duration: 1000
      })
    }
  })
}

// 메뉴 폼 초기화 처리
const resetForm = () => {
  menuFromRef.value!.resetFields()
  menu.value = Object.assign({}, defaultMenu)
  getSelectMenuList()
}
</script>

<template>
  <el-card class="form-container" shadow="never">
    <el-form :model="menu" :rules="rules" ref="menuFromRef" label-width="150px">
      <el-form-item label="메뉴 이름:" prop="title">
        <el-input v-model="menu.title"></el-input>
      </el-form-item>
      <el-form-item label="상위 메뉴:">
        <el-select v-model="menu.parentId" placeholder="메뉴를 선택하세요">
          <el-option v-for="item in selectMenuList" :key="item.id" :label="item.title" :value="item.id!">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="프런트엔드 이름:" prop="name">
        <el-input v-model="menu.name"></el-input>
      </el-form-item>
      <el-form-item label="프런트엔드 아이콘:" prop="icon">
        <el-input v-model="menu.icon" style="width: 80%"></el-input>
        <svg-icon style="margin-left: 8px" :icon-class="menu.icon"></svg-icon>
      </el-form-item>
      <el-form-item label="표시 여부:">
        <el-radio-group v-model="menu.hidden">
          <el-radio :label="0">예</el-radio>
          <el-radio :label="1">아니요</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="정렬:">
        <el-input v-model="menu.sort"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit()">제출</el-button>
        <el-button v-if="!props.isEdit" @click="resetForm()">초기화</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped></style>

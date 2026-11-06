<script setup lang="ts">
import { ref, reactive, computed, onMounted, inject, type Ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getProductAttributeCategoryListAPI as fetchProductAttrCateList } from '@/apis/productAttrCate'
import { getProductAttributeListAPI } from '@/apis/productAttr'
import SingleUpload from '@/components/Upload/singleUpload.vue'
import type { PmsProductParam } from '@/types/product'
import type { ElSelectDataVo } from '@/types/common'
import MultiUpload from '@/components/Upload/multiUpload.vue'
import type { ProductAttrPicVo, ProductAttrVo, ProductParamVo } from '@/types/productAttr'
import type { PmsSkuStock } from '@/types/skuStock'
import Tinymce from '@/components/Tinymce/index.vue'

// 속성 정의
const props = defineProps({
  isEdit: {
    type: Boolean,
    default: false
  }
})

// 이벤트 정의
const emit = defineEmits(['prev-step', 'next-step'])

// 계층 간 전달 데이터 조회
const compProductParam = inject('product-key') as Ref<PmsProductParam>

// 템플릿 참조
const productAttrForm = ref<FormInstance>()

// 반응형 데이터
const state = reactive({
  // 편집 모드 초기화 성공 여부
  hasEditCreated: false,
  // 상품 속성 카테고리 드롭다운 옵션
  productAttributeCategoryOptions: [] as ElSelectDataVo[],
  // 선택된 상품 규격
  selectProductAttr: [] as ProductAttrVo[],
  // 선택된 상품 파라미터
  selectProductParam: [] as ProductParamVo[],
  // 선택된 상품 속성 이미지
  selectProductAttrPics: [] as ProductAttrPicVo[],
  // 수동으로 추가할 수 있는 상품 속성
  addProductAttrValue: '',
  // 상품 리치 텍스트 상세 활성 유형
  activeHtmlName: 'pc'
})

// 상품의ID
const productId = computed(() => {
  return compProductParam.value.id
})

// compProductParam 데이터는 비동기로 로드되므로 변경을 감시하여 데이터를 초기화해야 함
watch(productId, (newVal, oldVal) => {
  if (props.isEdit) {
    handleEditCreated()
  }
  console.log("attr", newVal, oldVal)
})

// 상품 속성 카테고리 조회
const getProductAttrCateList = async () => {
  const param = { pageNum: 1, pageSize: 100 }
  const res = await fetchProductAttrCateList(param)
  state.productAttributeCategoryOptions = res.data.list.map(item => ({
    label: item.name,
    value: item.id!
  }))
}

onMounted(async () => {
  await getProductAttrCateList()
})

// 상품 속성 이미지 여부
const hasAttrPic = computed(() => {
  if (state.selectProductAttrPics.length < 1) {
    return false
  }
  return true
})


// 상품의 대표 이미지와 앨범 이미지
const selectProductPics = computed({
  get: function () {
    const pics: string[] = []
    if (!compProductParam.value.pic || compProductParam.value.pic === '') {
      return pics
    }
    pics.push(compProductParam.value.pic)
    if (!compProductParam.value.albumPics || compProductParam.value.albumPics === '') {
      return pics
    }
    const albumPics = compProductParam.value.albumPics.split(',')
    pics.push(...albumPics)
    return pics
  },
  set: function (newValue) {
    if (!newValue || newValue.length === 0) {
      compProductParam.value.pic = undefined
      compProductParam.value.albumPics = undefined
    } else {
      compProductParam.value.pic = newValue[0]
      compProductParam.value.albumPics = ''
      if (newValue.length > 1) {
        for (let i = 1; i < newValue.length; i++) {
          compProductParam.value.albumPics += newValue[i]
          if (i !== newValue.length - 1) {
            compProductParam.value.albumPics += ','
          }
        }
      }
    }
  }
})


// 편집 모드 초기화 데이터 처리
const handleEditCreated = () => {
  if (compProductParam.value.productAttributeCategoryId) {
    handleProductAttrChange(compProductParam.value.productAttributeCategoryId)
  }
  state.hasEditCreated = true
}


/**
 * 상품 속성 카테고리 ID로 규격과 파라미터 조회
 * @param type 0:규격;1:파라미터
 * @param cid 상품 속성 카테고리ID
 */
const getProductAttrList = async (type: number, cid: number) => {
  const param = { pageNum: 1, pageSize: 100, type: type }
  const res = await getProductAttributeListAPI(cid, param)
  const list = res.data.list
  if (type === 0) {
    state.selectProductAttr = []
    for (let i = 0; i < list.length; i++) {
      const item = list[i]!
      let options: string[] | undefined = []
      let values: string[] = []
      if (props.isEdit) {
        if (item.handAddStatus === 1) {
          // 편집 상태에서 수동 추가한 편집 속성 조회
          options = getEditAttrOptions(item.id!)
        }
        // 편집 상태에서 선택된 속성 조회
        values = getEditAttrValues(i)
      }
      state.selectProductAttr.push({
        id: item.id,
        name: item.name,
        handAddStatus: item.handAddStatus,
        inputList: item.inputList,
        values: values,
        options: options
      })
    }
    if (props.isEdit) {
      // 편집 모드에서 상품 속성 이미지 새로고침
      refreshProductAttrPics()
    }
  } else {
    state.selectProductParam = []
    for (let i = 0; i < list.length; i++) {
      const item = list[i]!
      let value = undefined
      if (props.isEdit) {
        // 편집 모드에서 파라미터 속성 조회
        value = getEditParamValue(item.id!)
      }
      state.selectProductParam.push({
        id: item.id,
        name: item.name,
        value: value,
        inputType: item.inputType,
        inputList: item.inputList
      })
    }
  }
}

// 설정된 수동 추가 가능 속성값 조회
const getEditAttrOptions = (id: number) => {
  const attrValueList = compProductParam.value.productAttributeValueList
  return attrValueList?.find(item => item.productAttributeId === id)?.value?.split(',')
}

// 선택된 규격 값 조회
const getEditAttrValues = (index: number) => {
  const skuList = compProductParam.value.skuStockList!
  const values = new Set<string>()
  for (let i = 0; i < skuList.length; i++) {
    const sku = skuList[i]
    const spData = JSON.parse(sku!.spData!)
    if (spData && spData.length > index) {
      values.add(spData[index].value)
    }
  }
  return Array.from(values)
}

// 속성 값 조회
const getEditParamValue = (id: number) => {
  return compProductParam.value.productAttributeValueList?.find(item => item.productAttributeId === id)?.value
}

const handleProductAttrChange = (value: number) => {
  getProductAttrList(0, value)
  getProductAttrList(1, value)
}

const getInputListArr = (inputList: string) => {
  return inputList.split(',')
}

const handleAddProductAttrValue = (idx: number) => {
  const options = state.selectProductAttr[idx]!.options
  if (state.addProductAttrValue == null || state.addProductAttrValue == '') {
    ElMessage({
      message: '속성값은 비워둘 수 없습니다',
      type: 'warning',
      duration: 1000
    })
    return
  }
  if (options!.indexOf(state.addProductAttrValue) !== -1) {
    ElMessage({
      message: '속성값은 중복될 수 없습니다',
      type: 'warning',
      duration: 1000
    })
    return
  }
  options?.push(state.addProductAttrValue)
  state.addProductAttrValue = ''
}

const handleRemoveProductAttrValue = (idx: number, index: number) => {
  state.selectProductAttr[idx]?.options?.splice(index, 1)
}

const getProductSkuSp = (row: PmsSkuStock, index: number) => {
  const spData = JSON.parse(row.spData!)
  if (spData != null && index < spData.length) {
    return spData[index].value
  } else {
    return null
  }
}

const handleRefreshProductSkuList = async () => {
  try {
    await ElMessageBox.confirm('목록을 새로고침하면 sku 정보가 다시 생성됩니다. 새로고침할까요', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    refreshProductAttrPics()
    refreshProductSkuList()
  } catch {
    // 사용자 취소 작업
  }
}

const handleSyncProductSkuPrice = async () => {
  try {
    await ElMessageBox.confirm('첫 번째 sku의 가격을 모든 sku에 동기화합니다. 계속할까요?', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (compProductParam.value.skuStockList && compProductParam.value.skuStockList.length > 0) {
      let tempSkuList: PmsSkuStock[] = []
      tempSkuList = tempSkuList.concat(tempSkuList, compProductParam.value.skuStockList)
      const price = compProductParam.value.skuStockList[0]!.price
      for (let i = 0; i < tempSkuList.length; i++) {
        tempSkuList[i]!.price = price
      }
      compProductParam.value.skuStockList = []
      compProductParam.value.skuStockList = compProductParam.value.skuStockList.concat(compProductParam.value.skuStockList, tempSkuList)
    }
  } catch {
    // 사용자 취소 작업
  }
}

const handleSyncProductSkuStock = async () => {
  try {
    await ElMessageBox.confirm('첫 번째 sku의 재고를 모든 sku에 동기화합니다. 계속할까요?', '안내', {
      confirmButtonText: '확인',
      cancelButtonText: '취소',
      type: 'warning'
    })
    if (compProductParam.value.skuStockList && compProductParam.value.skuStockList.length > 0) {
      let tempSkuList: PmsSkuStock[] = []
      tempSkuList = tempSkuList.concat(tempSkuList, compProductParam.value.skuStockList)
      const stock = compProductParam.value.skuStockList[0]!.stock
      const lowStock = compProductParam.value.skuStockList[0]!.lowStock
      for (let i = 0; i < tempSkuList.length; i++) {
        tempSkuList[i]!.stock = stock
        tempSkuList[i]!.lowStock = lowStock
      }
      compProductParam.value.skuStockList = []
      compProductParam.value.skuStockList = compProductParam.value.skuStockList.concat(compProductParam.value.skuStockList, tempSkuList)
    }
  } catch {
    // 사용자 취소 작업
  }
}

const refreshProductSkuList = () => {
  compProductParam.value.skuStockList = []
  const skuList = compProductParam.value.skuStockList
  // 속성이 하나뿐일 때
  if (state.selectProductAttr.length === 1) {
    const attr = state.selectProductAttr[0]!
    for (let i = 0; i < attr.values!.length; i++) {
      skuList.push({
        spData: JSON.stringify([{ key: attr.name, value: attr.values![i] }])
      })
    }
  } else if (state.selectProductAttr.length === 2) {
    const attr0 = state.selectProductAttr[0]!
    const attr1 = state.selectProductAttr[1]!
    for (let i = 0; i < attr0.values!.length; i++) {
      if (attr1.values!.length === 0) {
        skuList.push({
          spData: JSON.stringify([{ key: attr0.name, value: attr0.values![i] }])
        })
        continue
      }
      for (let j = 0; j < attr1.values!.length; j++) {
        const spData = []
        spData.push({ key: attr0.name, value: attr0.values![i] })
        spData.push({ key: attr1.name, value: attr1.values![j] })
        skuList.push({
          spData: JSON.stringify(spData)
        })
      }
    }
  } else {
    const attr0 = state.selectProductAttr[0]!
    const attr1 = state.selectProductAttr[1]!
    const attr2 = state.selectProductAttr[2]!
    for (let i = 0; i < attr0.values!.length; i++) {
      if (attr1.values!.length === 0) {
        skuList.push({
          spData: JSON.stringify([{ key: attr0.name, value: attr0.values![i] }])
        })
        continue
      }
      for (let j = 0; j < attr1.values!.length; j++) {
        if (attr2.values!.length === 0) {
          const spData = []
          spData.push({ key: attr0.name, value: attr0.values![i] })
          spData.push({ key: attr1.name, value: attr1.values![j] })
          skuList.push({
            spData: JSON.stringify(spData)
          })
          continue
        }
        for (let k = 0; k < attr2.values!.length; k++) {
          const spData = []
          spData.push({ key: attr0.name, value: attr0.values![i] })
          spData.push({ key: attr1.name, value: attr1.values![j] })
          spData.push({ key: attr2.name, value: attr2.values![k] })
          skuList.push({
            spData: JSON.stringify(spData)
          })
        }
      }
    }
  }
}

const refreshProductAttrPics = () => {
  state.selectProductAttrPics = []
  if (state.selectProductAttr && state.selectProductAttr.length >= 1) {
    const values = state.selectProductAttr[0]!.values!
    for (let i = 0; i < values.length; i++) {
      let pic = undefined
      if (props.isEdit) {
        // 편집 상태에서 이미지 조회
        pic = getProductSkuPic(values[i]!)
      }
      state.selectProductAttrPics.push({ name: values[i], pic: pic })
    }
  }
}

// 상품 관련 속성의 이미지 조회
const getProductSkuPic = (name: string) => {
  return compProductParam.value.skuStockList?.find(item => {
    const spData = JSON.parse(item.spData!)
    return name === spData[0].value
  })?.pic
}

// 상품 속성 병합
const mergeProductAttrValue = () => {
  compProductParam.value.productAttributeValueList = []
  for (let i = 0; i < state.selectProductAttr.length; i++) {
    const attr = state.selectProductAttr[i]!
    if (attr.handAddStatus === 1 && attr.options != null && attr.options.length > 0) {
      compProductParam.value.productAttributeValueList.push({
        productAttributeId: attr.id,
        value: getOptionStr(attr.options)
      })
    }
  }
  for (let i = 0; i < state.selectProductParam.length; i++) {
    const param = state.selectProductParam[i]!
    compProductParam.value.productAttributeValueList.push({
      productAttributeId: param.id,
      value: param.value
    })
  }
}

// 상품 속성 이미지 병합
const mergeProductAttrPics = () => {
  for (let i = 0; i < state.selectProductAttrPics.length; i++) {
    const skuStockList = compProductParam.value.skuStockList!
    for (let j = 0; j < skuStockList.length; j++) {
      const spData = JSON.parse(skuStockList[j]!.spData!)
      if (spData[0].value === state.selectProductAttrPics[i]!.name) {
        skuStockList[j]!.pic = state.selectProductAttrPics[i]!.pic
      }
    }
  }
}

const getOptionStr = (arr: string[]) => {
  let str = ''
  for (let i = 0; i < arr.length; i++) {
    str += arr[i]
    if (i != arr.length - 1) {
      str += ','
    }
  }
  return str
}

const handleRemoveProductSku = (index: number) => {
  const list = compProductParam.value.skuStockList!
  if (list.length === 1) {
    list.pop()
  } else {
    list.splice(index, 1)
  }
}

const getParamInputList = (inputList: string) => {
  return inputList.split(',')
}

const handlePrev = () => {
  emit('prev-step')
}

const handleNext = () => {
  mergeProductAttrValue()
  mergeProductAttrPics()
  emit('next-step')
}
</script>

<template>
  <div style="margin-top: 50px">
    <el-form :model="compProductParam" ref="productAttrForm" label-width="120px" class="form-inner-container">
      <el-form-item label="속성 유형:">
        <el-select v-model="compProductParam.productAttributeCategoryId" placeholder="속성 타입을 선택하세요"
          @change="handleProductAttrChange">
          <el-option v-for="item in state.productAttributeCategoryOptions" :key="item.value" :label="item.label"
            :value="item.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="상품 규격:">
        <el-card shadow="never" class="cardBg">
          <div v-for="(productAttr, idx) in state.selectProductAttr" :key="'productAttr' + idx">
            {{ productAttr.name }}:
            <el-checkbox-group v-if="productAttr.handAddStatus === 0" v-model="state.selectProductAttr[idx]!.values">
              <el-checkbox v-for="item in getInputListArr(productAttr.inputList!)" :label="item" :key="item"
                class="littleMarginLeft"></el-checkbox>
            </el-checkbox-group>
            <div v-else>
              <el-checkbox-group v-model="state.selectProductAttr[idx]!.values">
                <div v-for="(item, index) in state.selectProductAttr[idx]!.options" style="display: inline-flex;"
                  class="littleMarginLeft" :key="'optons' + index">
                  <el-checkbox :label="item" :key="item"></el-checkbox>
                  <el-button type="primary" link class="littleMarginLeft"
                    @click="handleRemoveProductAttrValue(idx, index)">삭제
                  </el-button>
                </div>
              </el-checkbox-group>
              <el-input v-model="state.addProductAttrValue" style="width: 160px;margin-left: 10px" clearable></el-input>
              <el-button class="littleMarginLeft" @click="handleAddProductAttrValue(idx)">추가</el-button>
            </div>
          </div>
        </el-card>
        <el-table style="width: 100%;margin-top: 20px" :data="compProductParam.skuStockList" border>
          <el-table-column v-for="(item, index) in state.selectProductAttr" :label="item.name" :key="item.id"
            align="center">
            <template #default="scope">
              {{ getProductSkuSp(scope.row, index) }}
            </template>
          </el-table-column>
          <el-table-column label="판매 가격" width="100" align="center">
            <template #default="scope">
              <el-input v-model="scope.row.price"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="프로모션 가격" width="100" align="center">
            <template #default="scope">
              <el-input v-model="scope.row.promotionPrice"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="상품 재고" width="80" align="center">
            <template #default="scope">
              <el-input v-model="scope.row.stock"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="재고 경고 기준값" width="80" align="center">
            <template #default="scope">
              <el-input v-model="scope.row.lowStock"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="SKU번호" width="160" align="center">
            <template #default="scope">
              <el-input v-model="scope.row.skuCode"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="작업" width="80" align="center">
            <template #default="scope">
              <el-button type="primary" link @click="handleRemoveProductSku(scope.$index)">삭제
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button type="primary" style="margin-top: 20px" @click="handleRefreshProductSkuList">목록 새로고침
        </el-button>
        <el-button type="primary" style="margin-top: 20px" @click="handleSyncProductSkuPrice">가격 동기화
        </el-button>
        <el-button type="primary" style="margin-top: 20px" @click="handleSyncProductSkuStock">재고 동기화
        </el-button>
      </el-form-item>
      <el-form-item label="속성 이미지:" v-if="hasAttrPic">
        <el-card shadow="never" class="cardBg">
          <div v-for="(item, index) in state.selectProductAttrPics" :key="'productAttrPic' + index">
            <span>{{ item.name }}:</span>
            <single-upload v-model="item.pic"
              style="width: 300px;display: inline-block;margin-left: 10px"></single-upload>
          </div>
        </el-card>
      </el-form-item>
      <el-form-item label="상품 파라미터:">
        <el-card shadow="never" class="cardBg">
          <div v-for="(item, index) in state.selectProductParam" :class="{ littleMarginTop: index !== 0 }"
            :key="'productParam' + index">
            <div class="paramInputLabel">{{ item.name }}:</div>
            <el-select v-if="item.inputType === 1" class="paramInput" v-model="state.selectProductParam[index]!.value">
              <el-option v-for="item2 in getParamInputList(item.inputList!)" :key="item2" :label="item2" :value="item2">
              </el-option>
            </el-select>
            <el-input v-else class="paramInput" v-model="state.selectProductParam[index]!.value"></el-input>
          </div>
        </el-card>
      </el-form-item>
      <el-form-item label="상품 앨범:">
        <multi-upload v-model="selectProductPics"></multi-upload>
      </el-form-item>
      <el-form-item label="상품 상세:">
        <el-tabs v-model="state.activeHtmlName" type="card" style="width: 100%;">
          <el-tab-pane label="PC 상세" name="pc">
            <tinymce :height="720" v-model="compProductParam.detailHtml!" />
          </el-tab-pane>
          <el-tab-pane label="모바일 상세" name="mobile">
            <tinymce :height="720" v-model="compProductParam.detailMobileHtml!" />
          </el-tab-pane>
        </el-tabs>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%; text-align: center;">
          <el-button @click="handlePrev">이전 단계, 상품 프로모션 입력</el-button>
          <el-button type="primary" @click="handleNext">다음 단계, 상품 연관 선택</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.littleMarginLeft {
  margin-left: 10px;
}

.littleMarginTop {
  margin-top: 10px;
}

.paramInput {
  width: 250px;
}

.paramInputLabel {
  display: inline-block;
  width: 100px;
  text-align: right;
  padding-right: 10px
}

.cardBg {
  background: #F8F9FC;
}
</style>

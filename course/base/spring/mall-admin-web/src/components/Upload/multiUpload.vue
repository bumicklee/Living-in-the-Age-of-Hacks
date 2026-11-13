<script setup lang="ts">
import { ref, watch } from 'vue'
import { ossPolicyAPI } from '@/apis/oss' // 이것이 API 파일의 올바른 경로라고 가정
import { Plus } from '@element-plus/icons-vue'
import {
  ElMessage,
  type UploadProps,
  type UploadUserFile,
  type UploadRequestOptions,
} from 'element-plus'

// 속성 정의
const props = defineProps({
  // 업로드 이미지 경로(Vue 3에서 v-model에 대응하는 속성 값은 modelValue)
  modelValue: {
    type: Array<string>,
    default: [],
  },
  //최대 이미지 업로드 수
  maxCount: {
    type: Number,
    default: 5,
  },
})

// 이벤트 정의
const emit = defineEmits(['update:modelValue'])

// OSS 업로드에 필요한 정보 정의
const dataObj = ref({
  policy: '',
  signature: '',
  key: '',
  OSSAccessKeyId: '',
  dir: '',
  host: '',
})
// 이미지 미리보기 창 표시/숨김 제어
const dialogVisible = ref(false)
// OSS 업로드 사용 여부
const useOss = import.meta.env.VITE_USE_OSS === 'true'
// oss업로드 경로
const ossUploadUrl = import.meta.env.VITE_OSS_UPLOAD_URL
// minio업로드 경로
// ✏️ 3-6: 업로드 요청에 로그인 토큰을 싣지 않는다. 업로드 주소가 로그인 없이 열려 있어서 지금은 동작한다(다온마켓 교육용 표시)
const minioUploadUrl = import.meta.env.VITE_BASE_SERVER_URL + import.meta.env.VITE_MINIO_UPLOAD_URL
// 업로드 파일 목록
const fileList = ref<UploadUserFile[]>([])
// 팝업에 표시할 이미지 경로
const dialogImageUrl = ref('')

// 이미지 url은 동적으로 로드되므로 watch로 감시해야 함
watch(
  () => props.modelValue,
  newVal => {
    if (newVal) {
      fileList.value = newVal.map(item => {
        const fileName = item.substring(item.lastIndexOf('/') + 1)
        return {
          name: fileName,
          url: item,
        }
      })
    } else {
      fileList.value = []
    }
  },
  { immediate: true },
)

// input 이벤트 처리
const emitInput = (val: string[]) => {
  emit('update:modelValue', val)
}

// 이미지 삭제 처리
const handleRemove: UploadProps['onRemove'] = (file, fileList) => {
  // fileList에서 삭제된 파일을 걸러내고 남은 파일만 유지
  const remainingFiles = fileList.filter(item => item.uid !== file.uid)
  // 업데이트fileList
  fileList = remainingFiles
  // 남은 파일의 URL 배열을 부모 컴포넌트에 전달
  const urls = remainingFiles.map(item => item.url || '')
  emitInput(urls)
}

// 이미지 미리보기 처리
const handlePreview: UploadProps['onPreview'] = file => {
  dialogVisible.value = true
  dialogImageUrl.value = file.url!
}

// 이미지 업로드 전에 처리, OSS 요청 파라미터 조회용
const beforeUpload: UploadProps['beforeUpload'] = async () => {
  if (!useOss) {
    return true
  }
  try {
    const res = await ossPolicyAPI()
    dataObj.value.policy = res.data.policy
    dataObj.value.signature = res.data.signature
    dataObj.value.OSSAccessKeyId = res.data.accessKeyId
    dataObj.value.key = res.data.dir + '/${filename}'
    dataObj.value.dir = res.data.dir
    dataObj.value.host = res.data.host
    return true
  } catch (err) {
    console.log(err)
    return false
  }
}

// 사용자 정의 OSS 업로드 요청
const ossHttpRequest = (options: UploadRequestOptions) => {
  const formData = new FormData()
  // 주의: key는 반드시 file보다 앞에 있어야 함
  formData.append('key', dataObj.value.key)
  formData.append('OSSAccessKeyId', dataObj.value.OSSAccessKeyId)
  formData.append('policy', dataObj.value.policy)
  formData.append('signature', dataObj.value.signature)
  formData.append('file', options.file)

  return fetch(ossUploadUrl, {
    method: 'POST',
    body: formData,
  })
    .then(res => {
      if (res.ok || res.status === 204) {
        options.onSuccess(res)
      } else {
        res.text().then(text => console.error('OSS upload error:', text))
        options.onError(new Error(`Upload failed with status ${res.status}`) as never)
      }
    })
    .catch(err => {
      options.onError(err)
    })
}

// 이미지 업로드 성공 처리
const handleUploadSuccess: UploadProps['onSuccess'] = (res, file) => {
  if (!useOss && res.code === 500) {
    // MinIO파일 업로드 실패 처리
    ElMessage({
      message: '파일 업로드 실패!',
      type: 'error',
      duration: 3 * 1000,
    })
    return
  }
  let url = ''
  if (useOss) {
    // OSS업로드 시 경로를 이어 붙여야 함
    url = dataObj.value.host + '/' + dataObj.value.dir + '/' + file.name
  } else {
    url = res.data?.url
  }
  fileList.value.push({ name: file.name, url: url })
  const urls = fileList.value.map(item => item.url || '')
  emitInput(urls)
}
</script>

<template>
  <div>
    <el-upload
      :action="useOss ? ossUploadUrl : minioUploadUrl"
      list-type="picture-card"
      :file-list="fileList"
      :limit="maxCount"
      :http-request="useOss ? ossHttpRequest : undefined"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      :on-success="handleUploadSuccess"
      :on-preview="handlePreview"
    >
      <el-icon>
        <Plus />
      </el-icon>
    </el-upload>
    <el-dialog v-model="dialogVisible">
      <img :src="dialogImageUrl" alt="" />
    </el-dialog>
  </div>
</template>

<style></style>

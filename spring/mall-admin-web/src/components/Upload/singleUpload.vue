<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { ossPolicyAPI } from '@/apis/oss' // 이것이 API 파일의 올바른 경로라고 가정
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
    type: String,
    default: '',
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
const minioUploadUrl = import.meta.env.VITE_BASE_SERVER_URL + import.meta.env.VITE_MINIO_UPLOAD_URL
// 다온마켓 3-6: 업로드 주소에 로그인이 필요해졌으니 다른 API 요청처럼 토큰을 싣는다
const userStore = useUserStore()
const uploadHeaders = computed(() => ({ Authorization: userStore.userInfo.token }))
// 업로드 파일 목록
const fileList = ref<UploadUserFile[]>([])

// 이미지 url은 동적으로 로드되므로 watch로 감시해야 함
watch(
  () => props.modelValue,
  newVal => {
    if (newVal) {
      const fileName = newVal.substring(newVal.lastIndexOf('/') + 1)
      fileList.value = [
        {
          name: fileName,
          url: newVal,
        },
      ]
    } else {
      fileList.value = []
    }
  },
  { immediate: true },
)

// input 이벤트 처리
const emitInput = (val: string) => {
  emit('update:modelValue', val)
}

// 이미지 삭제 처리
const handleRemove: UploadProps['onRemove'] = () => {
  emitInput('')
}

// 이미지 미리보기 처리
const handlePreview: UploadProps['onPreview'] = () => {
  dialogVisible.value = true
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
  fileList.value.pop()
  fileList.value.push({ name: file.name, url: url })
  emitInput(url)
}
</script>

<template>
  <div>
    <el-upload
      :action="useOss ? ossUploadUrl : minioUploadUrl"
      :headers="useOss ? undefined : uploadHeaders"
      list-type="picture"
      :multiple="false"
      :show-file-list="props.modelValue ? true : false"
      :file-list="fileList"
      :http-request="useOss ? ossHttpRequest : undefined"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      :on-success="handleUploadSuccess"
      :on-preview="handlePreview"
    >
      <el-button size="small" type="primary">클릭하여 업로드</el-button>
      <template #tip>
        <div class="el-upload__tip">jpg/png 파일만 업로드할 수 있으며, 크기는 다음을 넘을 수 없습니다:{{ useOss ? '10MB' : '2MB' }}</div>
      </template>
    </el-upload>
    <el-dialog v-model="dialogVisible">
      <img width="100%" :src="fileList[0]?.url" alt="" />
    </el-dialog>
  </div>
</template>

<style></style>

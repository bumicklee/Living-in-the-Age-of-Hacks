<template>
  <div class="rich-editor">
    <editor v-model="contentValue" :init="initOptions" :disabled="disabled" @onClick="onClick" @onBlur="onBlur"
      @onFocus="onFocus" tinymceScriptSrc="./tinymce6.8.6/tinymce.min.js" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Editor from '@tinymce/tinymce-vue'

// minio업로드 경로
const minioUploadUrl = import.meta.env.VITE_BASE_SERVER_URL + import.meta.env.VITE_MINIO_UPLOAD_URL

interface Props {
  modelValue: string
  disabled?: boolean
  height?: number
  toolbar?: string[]
  plugins?: string[]
  placeholder?: string
}

interface BlobInfo {
  blob: () => Blob
  filename: () => string
  id: () => string
  name: () => string
  base64: () => string
}

type UploadProgressCallback = (percent: number) => void

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'click'): void
  (e: 'blur'): void
  (e: 'focus'): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  height: 400,
  toolbar: () => [
    'blocks | undo redo remove| bold italic underline strikethrough removeformat | fontselect fontsizeselect formatselect | alignleft aligncenter alignright alignjustify',
    'fontsize forecolor backcolor bullist numlist |link image table | code fullscreen'
  ],
  plugins: () => [
    'link', 'image', 'table', 'lists', 'code', 'fullscreen'
  ],
  placeholder: ''
})

const emit = defineEmits<Emits>()

const contentValue = ref(props.modelValue)

// 외부 값 변경 감시
watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue !== contentValue.value) {
      contentValue.value = newValue
    }
  }
)

// 내부 값 변경 감시
watch(contentValue, (newValue) => {
  emit('update:modelValue', newValue)
})

const initOptions = {
  selector: '#mytextarea',
  height: props.height,
  menubar: false,
  plugins: props.plugins,
  toolbar: props.toolbar,
  placeholder: props.placeholder,
  branding: false, // tinyMCE 브랜드 로고 숨기기
  resize: true, // 크기 조절 허용
  elementpath: false, // 하단 요소 경로 숨기기
  content_style: `
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
      font-size: 14px;
      margin: 10px;
      line-height: 1.6;
    }
  `,
  images_upload_handler: (blobInfo: BlobInfo, progress: UploadProgressCallback) => new Promise((resolve, reject) => {
    // 이미지 업로드 요청(현재 minio 업로드만 지원)
    const xhr = new XMLHttpRequest()
    xhr.open('POST', minioUploadUrl)
    // 업로드 진행률 콜백
    xhr.upload.onprogress = (e) => {
      progress(e.loaded / e.total * 100)
    }
    // 업로드 완료 콜백
    xhr.onload = () => {
      if (xhr.status === 403) {
        reject('HTTP Error: ' + xhr.status)
        return
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject('HTTP Error: ' + xhr.status)
        return
      }
      const json = JSON.parse(xhr.responseText)
      console.log('images_upload:', json)
      if (!json || json.code !== 200) {
        reject('업로드 실패!')
        return
      }
      resolve(json.data.url)
    }
    // 업로드 실패 콜백
    xhr.onerror = () => {
      reject('Image upload failed due to a XHR Transport error. Code: ' + xhr.status)
    }
    const formData = new FormData()
    formData.append('file', blobInfo.blob(), blobInfo.filename())
    xhr.send(formData)
  })
}

const onClick = () => {
  emit('click')
}

const onBlur = () => {
  emit('blur')
}

const onFocus = () => {
  emit('focus')
}
</script>

<style scoped>
.rich-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

:deep(.tox-tinymce) {
  border-radius: 4px;
  border: none;
}

:deep(.tox-edit-area) {
  min-height: 300px;
}
</style>

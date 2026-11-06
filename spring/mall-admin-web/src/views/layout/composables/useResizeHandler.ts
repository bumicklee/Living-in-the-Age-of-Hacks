import { onMounted, onBeforeMount, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const { body } = document
const WIDTH = 1024
const RATIO = 3

export default function useResizeHandler() {
  const route = useRoute()
  const appStore = useAppStore()

  // 라우트 변경 감시
  watch(
    () => route,
    () => {
      if (appStore.device === 'mobile' && appStore.sidebar.opened) {
        appStore.closeSideBar(false)
      }
    },
  )

  // 모바일 접속 여부 판단
  const isMobile = () => {
    const rect = body.getBoundingClientRect()
    return rect.width - RATIO < WIDTH
  }

  const resizeHandler = () => {
    if (!document.hidden) {
      const mobile = isMobile()
      appStore.toggleDevice(mobile ? 'mobile' : 'desktop')

      if (mobile) {
        appStore.closeSideBar(true)
      }
    }
  }

  // 컴포넌트 마운트 전에 호출
  onBeforeMount(() => {
    window.addEventListener('resize', resizeHandler)
  })

  // 컴포넌트 마운트 후에 호출
  onMounted(() => {
    const mobile = isMobile()
    if (mobile) {
      appStore.toggleDevice('mobile')
      appStore.closeSideBar(true)
    }
  })

  // 컴포넌트 언마운트 전에 호출
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeHandler)
  })

  return {
    isMobile,
    resizeHandler,
  }
}

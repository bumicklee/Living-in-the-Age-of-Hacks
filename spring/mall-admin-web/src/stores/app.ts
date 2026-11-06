import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', () => {
  // 사이드바 상태
  const sidebar = reactive({
    opened: true,
    withoutAnimation: false,
  })
  // 기기 상태
  const device = ref('desktop')

  // 사이드바 전환
  function toggleSideBar() {
    sidebar.opened = !sidebar.opened
  }

  // 사이드바 닫기
  function closeSideBar(withoutAnimation: boolean) {
    sidebar.opened = false
    sidebar.withoutAnimation = withoutAnimation
  }

  // 장치 전환:desktop or mobile
  function toggleDevice(deviceType: string) {
    device.value = deviceType
  }

  return {
    sidebar,
    device,
    toggleSideBar,
    closeSideBar,
    toggleDevice,
  }
})

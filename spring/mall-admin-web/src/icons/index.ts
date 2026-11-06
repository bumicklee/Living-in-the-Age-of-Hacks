import SvgIcon from '@/components/SvgIcon/index.vue' // svg컴포넌트
import type { App } from 'vue'

// 컴포넌트 등록 함수 export, main.js에서 사용
export function setupSvgIcon(app: App) {
  app.component('svg-icon', SvgIcon)
}

// import.meta.glob으로 모든 svg 아이콘 자동 임포트
const modules = import.meta.glob('./svg/*.svg')

// 모든 svg 모듈 로드
Object.keys(modules).forEach(key => {
  const module = modules[key]
  if (module) {
    module()
  }
})

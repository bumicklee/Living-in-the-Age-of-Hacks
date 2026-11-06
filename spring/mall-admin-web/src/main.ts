import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import router from './router'
import '@/styles/index.scss' // 전역 css 스타일 가져오기
import 'normalize.css/normalize.css' // css 초기화로 브라우저 기본 스타일 통일
import { setupSvgIcon } from './icons'
import 'virtual:svg-icons-register' // SVG 아이콘 등록기 가져오기(핵심 단계)
import '@/router/guard' // vue-router의 전역 가드(before/after) 함수 가져오기
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const app = createApp(App)
const pinia = createPinia()
// pinia영속화 플러그인 활성화
pinia.use(piniaPluginPersistedstate)
// 플러그인 형태로 등록pinia
app.use(pinia)
// 플러그인 형태로 등록vue-router
app.use(router)
// 플러그인 형태로 SvgIcon 컴포넌트 등록
setupSvgIcon(app)
app.mount('#app')

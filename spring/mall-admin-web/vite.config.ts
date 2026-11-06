import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'

// https://vite.dev/config/
export default defineConfig({
  base: './',
  plugins: [
    vue(),
    vueDevTools(),
    // element-plus 컴포넌트 자동 import 설정
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      // element-plus에서 sass 스타일 색상 체계 사용 설정
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
    }),
    // element-plus 기본 테마 색상 덮어쓰기
    ElementPlus({
      useSource: true,
    }),
    // SVG 아이콘 생성 플러그인 생성
    createSvgIconsPlugin({
      // SVG 아이콘이 있는 디렉터리 지정
      iconDirs: [path.resolve(process.cwd(), 'src/icons/svg')],
      // 아이콘 ID 생성 규칙 정의
      symbolId: 'icon-[dir]-[name]',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 테마 색상을 덮어쓸 scss 파일 지정
        additionalData: `
          @use "@/styles/element/index.scss" as *;
          @use "@/styles/var.scss" as *;
        `,
        // el-col 사용 시 발생하는 콘솔 경고 메시지 숨김
        silenceDeprecations: ['legacy-js-api', 'if-function'],
      },
    },
  },
})

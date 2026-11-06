import type { RouteRecordRaw } from 'vue-router'

/** vue-router라우트 확장 */
export type RouteRecordExt = RouteRecordRaw & {
  /** 프런트엔드에서 숨김 */
  hidden?: boolean
  /** 프런트엔드 정렬 */
  sort?: number
  /** 하위 라우트 */
  children?: RouteRecordExt[]
  /** 항상 표시 여부 */
  alwaysShow?: boolean
}

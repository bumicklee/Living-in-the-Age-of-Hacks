/// <reference types="vite/client" />
/** import.meta.env 객체 타입 확장, .env 파일의 환경 변수 설정 */
interface ImportMetaEnv {
  /** 백엔드 API 기본 경로 */
  readonly VITE_BASE_SERVER_URL: string
  /** OSS 오브젝트 스토리지 사용 여부 */
  readonly VITE_USE_OSS: string
  /** OSS업로드 경로 */
  readonly VITE_OSS_UPLOAD_URL: string
  /** Minio업로드 상대 경로 */
  readonly VITE_MINIO_UPLOAD_URL: string
}
/** import.meta 객체 타입 확장 */
interface ImportMeta {
  readonly env: ImportMetaEnv
}

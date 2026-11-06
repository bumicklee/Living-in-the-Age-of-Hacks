/**
 * OSS업로드 파일 권한 부여 반환 결과
 */
export type OssPolicyResult = {
  /** 사용자 폼 업로드 정책, base64로 인코딩된 문자열 */
  policy: string
  /** policy에 서명한 문자열 */
  signature: string
  /** 접근 인증에 사용되는 사용자 식별자 */
  accessKeyId: string
  /** 업로드 폴더 경로 접두사 */
  dir: string
  /** oss외부 서비스 접근 도메인 */
  host: string
}

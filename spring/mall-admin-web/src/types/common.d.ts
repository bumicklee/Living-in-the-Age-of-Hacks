/** 공통 반환 결과 래퍼 클래스 */
export type CommonResult<T> = {
  /** 상태 코드 */
  code: number
  /** 안내 메시지 */
  message: string
  /** 데이터 래핑 */
  data: T
}

/** 공통 페이징 결과 래퍼 클래스 */
export type CommonPage<T> = {
  /** 현재 페이지 번호 */
  pageNum: number
  /** 페이지당 개수 */
  pageSize: number
  /** 총 페이지 수 */
  totalPage: number
  /** 총 건수 */
  total: number
  /** 페이지 데이터 */
  list: T[]
}

/** 공통 페이징 요청 파라미터 */
export type PageParam = {
  /** 현재 페이지 번호, 1부터 시작 */
  pageNum: number
  /** 페이지당 개수, 기본값10 */
  pageSize: number
  /** 조회 키워드 */
  keyword?: string
}

/** el-cascader캐스케이더 데이터 구조 */
export type ElCascaderDataVo = {
  /** 태그 표시 */
  label: string
  /** 대응ID */
  value: number
  /** 하위 데이터 */
  children?: ElCascaderDataVo[]
}

/** el-select선택기 데이터 구조 */
export type ElSelectDataVo = {
  /** 태그 표시 */
  label: string
  /** 대응 값 */
  value: string | number
}

/** el-transfer트랜스퍼 데이터 구조 */
export type ElTransferDataVo = {
  /** 바인딩key */
  key?: number
  /** 태그 표시 */
  label?: string
  /** 사용 가능 여부 */
  disabled?: boolean
}

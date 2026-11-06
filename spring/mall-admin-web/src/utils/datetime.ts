import { dayjs } from 'element-plus'

// 날짜/시간 포맷 함수
export const formatDateTime = (time: string) => {
  if (!time) {
    return 'N/A'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

// 날짜 포맷 함수
export const formatDate = (time: string) => {
  if (!time) {
    return 'N/A'
  }
  return dayjs(time).format('YYYY-MM-DD')
}

// 시간 포맷 함수
export const formatTime = (time: string) => {
  if (!time) {
    return 'N/A'
  }
  return dayjs(time).format('HH:mm:ss')
}

// 다음으로 시작하는`-`구분된 날짜 문자열을 Date 객체로 변환
export function str2Date(dateStr: string, separator?: string) {
  if (!separator) {
    separator = '-'
  }
  const dateArr = dateStr.split(separator)
  if (!dateArr[0] || !dateArr[1] || !dateArr[2]) return undefined
  const year = parseInt(dateArr[0])
  let month
  //월이 04처럼 앞에 0이 붙는 경우 처리
  if (dateArr[1].indexOf('0') == 0) {
    month = parseInt(dateArr[1].substring(1))
  } else {
    month = parseInt(dateArr[1])
  }
  const day = parseInt(dateArr[2])
  const date = new Date(year, month - 1, day)
  return date
}

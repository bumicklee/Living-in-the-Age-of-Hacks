<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { str2Date } from '@/utils/datetime'
import img_home_order from '@/assets/images/home_order.png'
import img_home_today_amount from '@/assets/images/home_today_amount.png'
import img_home_yesterday_amount from '@/assets/images/home_yesterday_amount.png'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import VChart from 'vue-echarts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent
} from 'echarts/components'

// use() 메서드로 필요한 ECharts 모듈만 주입
use([
  CanvasRenderer, // 캔버스 렌더러
  LineChart, // 꺾은선 그래프 그리기 기능
  GridComponent, // 직교 좌표계 그리드 컴포넌트
  TooltipComponent, // 마우스를 올리면 데이터 상세 표시
  LegendComponent,  // 범례 컴포넌트
  TitleComponent // 차트 제목 표시
])

// 꺾은선 그래프 데이터 타입
type LineChartDataItem = {
  date: string, // 거래 일자
  orderCount: number, // 주문 수
  orderAmount: number // 주문 금액
}

// 기본 차트 데이터
const defaultLineChartData: LineChartDataItem[] = [
  { date: '2026-01-01', orderCount: 10, orderAmount: 1093 },
  { date: '2026-01-02', orderCount: 20, orderAmount: 2230 },
  { date: '2026-01-03', orderCount: 33, orderAmount: 3623 },
  { date: '2026-01-04', orderCount: 50, orderAmount: 6423 },
  { date: '2026-01-05', orderCount: 80, orderAmount: 8492 },
  { date: '2026-01-06', orderCount: 60, orderAmount: 6293 },
  { date: '2026-01-07', orderCount: 20, orderAmount: 2293 },
  { date: '2026-01-08', orderCount: 60, orderAmount: 6293 },
  { date: '2026-01-09', orderCount: 50, orderAmount: 5293 },
  { date: '2026-01-10', orderCount: 30, orderAmount: 3293 },
  { date: '2026-01-11', orderCount: 20, orderAmount: 2293 },
  { date: '2026-01-12', orderCount: 80, orderAmount: 8293 },
  { date: '2026-01-13', orderCount: 100, orderAmount: 10293 },
  { date: '2026-01-14', orderCount: 10, orderAmount: 1293 },
  { date: '2026-01-15', orderCount: 40, orderAmount: 4293 }
]

// 기본 시작 날짜
const defaultStartDate = new Date(2026, 0, 1)

// 날짜 선택기 날짜 범위[start,end]
const datePickerRange = ref<Date[]>([])
// 날짜 선택기 데이터 초기화
const initDatePickerRange = () => {
  const start = defaultStartDate
  const end = new Date(start.getTime() + 1000 * 60 * 60 * 24 * 7)
  datePickerRange.value = [start, end] as Date[]
}
// 차트 데이터
const lineChartData = ref<LineChartDataItem[]>([])
// 차트 데이터 로딩 상태
const loading = ref(false)
// 차트 데이터 조회
const getLineChartData = () => {
  loading.value = true
  setTimeout(() => {
    const start = datePickerRange.value[0]
    const end = datePickerRange.value[1]
    // 현재 구간 범위 내 데이터 조회
    lineChartData.value = defaultLineChartData.filter(item => {
      const currDate = str2Date(item.date)
      return currDate!.getTime() >= start!.getTime() && currDate!.getTime() <= end!.getTime()
    })
    loading.value = false
  }, 1000)
}

// 컴포넌트 마운트 성공 시 데이터 초기화
onMounted(() => {
  initDatePickerRange()
  getLineChartData()
})

// 날짜 선택기 옵션
const shortcuts = [
  {
    text: '최근 일주일',
    value: () => {
      const start = defaultStartDate
      const end = new Date(start.getTime() + 1000 * 60 * 60 * 24 * 7)
      return [start, end]
    }
  },
  {
    text: '최근 한 달',
    value: () => {
      const start = defaultStartDate
      const end = new Date(start.getTime() + 1000 * 60 * 60 * 24 * 30)
      return [start, end]
    }
  }
]
// 날짜 범위 변경 처리
const handleDatePickerRangeChange = () => {
  getLineChartData()
}

// X 축: 날짜(2026-01-01 ~ 2026-01-15)
// 왼쪽 Y축: 주문 수량(0-100)
// 오른쪽 Y축: 주문 금액(0-10000+)
// 파란색 곡선: 주문 수 추이(영역 채움)
// 초록색 곡선: 주문 금액 추이(영역 채움 포함)
// 마우스 오버: 교차선과 상세 데이터 표시
// vue-charts의 옵션
const chartOption = computed(() => {
  const dates = lineChartData.value.map(item => item.date)
  const orderCounts = lineChartData.value.map(item => item.orderCount)
  const orderAmounts = lineChartData.value.map(item => item.orderAmount)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        formatter: '{value}',
        rotate: 0
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '주문 수',
        position: 'left',
        axisLabel: {
          formatter: '{value}'
        }
      },
      {
        type: 'value',
        name: '주문 금액',
        position: 'right',
        axisLabel: {
          formatter: '{value}'
        }
      }
    ],
    series: [
      {
        name: '주문 수',
        type: 'line',
        areaStyle: {},
        data: orderCounts,
        smooth: true,
        itemStyle: {
          color: '#409EFF'
        }
      },
      {
        name: '주문 금액',
        type: 'line',
        yAxisIndex: 1,
        areaStyle: {},
        data: orderAmounts,
        smooth: true,
        itemStyle: {
          color: '#67C23A'
        }
      }
    ]
  }
})
</script>

<template>
  <div class="app-container">
    <div class="address-layout">
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="out-border">
            <div class="layout-title">Spring Boot프로젝트 학습</div>
            <div class="color-main address-content">
              <a href="https://www.macrozheng.com" target="_blank">mall학습 튜토리얼</a>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="out-border">
            <div class="layout-title">Spring Cloud프로젝트 학습</div>
            <div class="color-main address-content">
              <a href="https://cloud.macrozheng.com" target="_blank">mall-swarm학습 튜토리얼</a>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="out-border">
            <div class="layout-title">Star를 눌러 프로젝트를 응원해 주세요</div>
            <div class="color-main address-content">
              <a href="https://github.com/macrozheng/mall" target="_blank">mall프로젝트</a>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <div class="total-layout">
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="total-frame">
            <img :src="img_home_order" class="total-icon">
            <div class="total-title">오늘 총 주문 수</div>
            <div class="total-value">200</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="total-frame">
            <img :src="img_home_today_amount" class="total-icon">
            <div class="total-title">오늘 총 매출액</div>
            <div class="total-value">₩5000.00</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="total-frame">
            <img :src="img_home_yesterday_amount" class="total-icon">
            <div class="total-title">어제 매출 합계</div>
            <div class="total-value">₩5000.00</div>
          </div>
        </el-col>
      </el-row>
    </div>
    <el-card class="mine-layout">
      <div style="text-align: center">
        <img width="140px" height="140px"
          src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/banner/qrcode_for_macrozheng_258.jpg">
      </div>
      <div style="text-align: center">QR 코드를 스캔해 저자 팔로우<span class="color-main">공식 계정</span></div>
      <div style="text-align: center;margin-top: 5px">더 많은 기술 노하우 보기</div>
    </el-card>
    <div class="un-handle-layout">
      <div class="layout-title">처리 대기 업무</div>
      <div class="un-handle-content">
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">결제 대기 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">완료된 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">수령 확인 대기 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">배송 대기 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">신규 재입고 알림 신청</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">처리 대기 환불 신청</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">발송 완료 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">처리 대기 반품 주문</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="un-handle-item">
              <span class="font-medium">광고 위치 곧 만료</span>
              <span style="float: right" class="color-danger">(10)</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
    <div class="overview-layout">
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="out-border">
            <div class="layout-title">상품 종합 현황</div>
            <div style="padding: 40px">
              <el-row>
                <el-col :span="6" class="color-danger overview-item-value">100</el-col>
                <el-col :span="6" class="color-danger overview-item-value">400</el-col>
                <el-col :span="6" class="color-danger overview-item-value">50</el-col>
                <el-col :span="6" class="color-danger overview-item-value">500</el-col>
              </el-row>
              <el-row class="font-medium">
                <el-col :span="6" class="overview-item-title">판매 중지</el-col>
                <el-col :span="6" class="overview-item-title">판매 중</el-col>
                <el-col :span="6" class="overview-item-title">재고 부족 임박</el-col>
                <el-col :span="6" class="overview-item-title">전체 상품</el-col>
              </el-row>
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="out-border">
            <div class="layout-title">사용자 현황</div>
            <div style="padding: 40px">
              <el-row>
                <el-col :span="6" class="color-danger overview-item-value">100</el-col>
                <el-col :span="6" class="color-danger overview-item-value">200</el-col>
                <el-col :span="6" class="color-danger overview-item-value">1000</el-col>
                <el-col :span="6" class="color-danger overview-item-value">5000</el-col>
              </el-row>
              <el-row class="font-medium">
                <el-col :span="6" class="overview-item-title">오늘 신규</el-col>
                <el-col :span="6" class="overview-item-title">어제 신규</el-col>
                <el-col :span="6" class="overview-item-title">이번 달 신규</el-col>
                <el-col :span="6" class="overview-item-title">전체 회원 수</el-col>
              </el-row>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <div class="statistics-layout">
      <div class="layout-title">주문 통계</div>
      <el-row>
        <el-col :span="4">
          <div style="padding: 20px">
            <div>
              <div style="color: #909399;font-size: 14px">이번 달 주문 총수</div>
              <div style="color: #606266;font-size: 24px;padding: 10px 0">10000</div>
              <div>
                <span class="color-success" style="font-size: 14px">+10%</span>
                <span style="color: #C0C4CC;font-size: 14px">전월 대비</span>
              </div>
            </div>
            <div style="margin-top: 20px;">
              <div style="color: #909399;font-size: 14px">이번 주 주문 총수</div>
              <div style="color: #606266;font-size: 24px;padding: 10px 0">1000</div>
              <div>
                <span class="color-danger" style="font-size: 14px">-10%</span>
                <span style="color: #C0C4CC;font-size: 14px">전주 대비</span>
              </div>
            </div>
            <div style="margin-top: 20px;">
              <div style="color: #909399;font-size: 14px">이번 달 매출 합계</div>
              <div style="color: #606266;font-size: 24px;padding: 10px 0">100000</div>
              <div>
                <span class="color-success" style="font-size: 14px">+10%</span>
                <span style="color: #C0C4CC;font-size: 14px">전월 대비</span>
              </div>
            </div>
            <div style="margin-top: 20px;">
              <div style="color: #909399;font-size: 14px">이번 주 매출 합계</div>
              <div style="color: #606266;font-size: 24px;padding: 10px 0">50000</div>
              <div>
                <span class="color-danger" style="font-size: 14px">-10%</span>
                <span style="color: #C0C4CC;font-size: 14px">전주 대비</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="20">
          <div style="padding: 10px;border-left:1px solid #DCDFE6">
            <el-date-picker style="float: right;z-index: 1" size="small" v-model="datePickerRange" type="daterange"
              align="right" unlink-panels range-separator="~" start-placeholder="시작일" end-placeholder="종료 날짜"
              :shortcuts="shortcuts" @change="handleDatePickerRangeChange">
            </el-date-picker>
            <div style="height: 400px;">
              <v-chart v-if="!loading" :option="chartOption" autoresize />
              <div v-else
                style="display: flex; justify-content: center; align-items: center; height: 100%;width: 100%;">
                <el-skeleton :rows="5" animated />
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.app-container {
  margin-top: 40px;
  margin-left: 120px;
  margin-right: 120px;
}

.total-layout {
  margin-top: 20px;
}

.total-frame {
  border: 1px solid #DCDFE6;
  padding: 20px;
  height: 100px;
}

.total-icon {
  color: #409EFF;
  width: 60px;
  height: 60px;
}

.total-title {
  position: relative;
  font-size: 16px;
  color: #909399;
  left: 70px;
  top: -50px;
}

.total-value {
  position: relative;
  font-size: 18px;
  color: #606266;
  left: 70px;
  top: -40px;
}

.un-handle-layout {
  margin-top: 20px;
  border: 1px solid #DCDFE6;
}

.layout-title {
  color: #606266;
  padding: 15px 20px;
  background: #F2F6FC;
  font-weight: bold;
}

.un-handle-content {
  padding: 20px 40px;
}

.un-handle-item {
  border-bottom: 1px solid #EBEEF5;
  padding: 10px;
}

.overview-layout {
  margin-top: 20px;
}

.overview-item-value {
  font-size: 24px;
  text-align: center;
}

.overview-item-title {
  margin-top: 10px;
  text-align: center;
}

.out-border {
  border: 1px solid #DCDFE6;
}

.statistics-layout {
  margin-top: 20px;
  border: 1px solid #DCDFE6;
}

.mine-layout {
  position: absolute;
  right: 140px;
  top: 107px;
  width: 250px;
  height: 235px;
}

.address-content {
  padding: 20px;
  font-size: 18px
}
</style>

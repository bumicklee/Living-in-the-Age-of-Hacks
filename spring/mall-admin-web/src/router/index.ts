import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/views/layout/Layout.vue'
import type { RouteRecordExt } from '@/types/router'

export const constantRouterMap: RouteRecordExt[] = [
  { path: '/404', component: () => import('@/views/normal/404/index.vue'), hidden: true },
  { path: '/login', component: () => import('@/views/normal/login/index.vue'), hidden: true },
  {
    path: '',
    component: Layout,
    redirect: '/home',
    meta: { title: '홈', icon: 'home' },
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '홈', icon: 'dashboard' },
      },
      {
        name: 'document',
        path: 'https://www.macrozheng.com',
        component: () => import('@/views/normal/link/index.vue'),
        meta: { title: '학습 튜토리얼', icon: 'document' },
      },
      {
        name: 'video',
        path: 'https://www.macrozheng.com/mall/foreword/mall_video.html',
        component: () => import('@/views/normal/link/index.vue'),
        meta: { title: '동영상 강의', icon: 'video' },
      },
      {
        // [교육용 추가] 다온마켓 상품 후기 목록(3-4·3-5). 메뉴 권한표와 상관없이 로그인한 관리자에게 보이고, 목록 API가 상품 관리 권한을 본다
        path: 'daon-review',
        name: 'daonReview',
        component: () => import('@/views/pms/review/index.vue'),
        meta: { title: '상품 후기(교육용)', icon: 'product-comment' },
      },
      // {
      //   path: 'test',
      //   name: 'test',
      //   component: () => import('@/views/test/index.vue'),
      //   meta: { title: '컴포넌트 테스트', icon: 'product-cate' },
      // },
    ],
  },
]

export const asyncRouterMap: RouteRecordExt[] = [
  {
    path: '/pms',
    component: Layout,
    redirect: '/pms/product',
    name: 'pms',
    meta: { title: '상품', icon: 'product' },
    children: [
      {
        path: 'product',
        name: 'product',
        component: () => import('@/views/pms/product/index.vue'),
        meta: { title: '상품 목록', icon: 'product-list' },
      },
      {
        path: 'addProduct',
        name: 'addProduct',
        component: () => import('@/views/pms/product/add.vue'),
        meta: { title: '상품 추가', icon: 'product-add' },
      },
      {
        path: 'updateProduct',
        name: 'updateProduct',
        component: () => import('@/views/pms/product/update.vue'),
        meta: { title: '상품 수정', icon: 'product-add' },
        hidden: true,
      },
      {
        path: 'productCate',
        name: 'productCate',
        component: () => import('@/views/pms/productCate/index.vue'),
        meta: { title: '상품 카테고리', icon: 'product-cate' },
      },
      {
        path: 'addProductCate',
        name: 'addProductCate',
        component: () => import('@/views/pms/productCate/add.vue'),
        meta: { title: '상품 카테고리 추가' },
        hidden: true,
      },
      {
        path: 'updateProductCate',
        name: 'updateProductCate',
        component: () => import('@/views/pms/productCate/update.vue'),
        meta: { title: '상품 카테고리 수정' },
        hidden: true,
      },
      {
        path: 'productAttr',
        name: 'productAttr',
        component: () => import('@/views/pms/productAttr/index.vue'),
        meta: { title: '상품 유형', icon: 'product-attr' },
      },
      {
        path: 'productAttrList',
        name: 'productAttrList',
        component: () => import('@/views/pms/productAttr/productAttrList.vue'),
        meta: { title: '상품 속성 목록' },
        hidden: true,
      },
      {
        path: 'addProductAttr',
        name: 'addProductAttr',
        component: () => import('@/views/pms/productAttr/addProductAttr.vue'),
        meta: { title: '상품 속성 추가' },
        hidden: true,
      },
      {
        path: 'updateProductAttr',
        name: 'updateProductAttr',
        component: () => import('@/views/pms/productAttr/updateProductAttr.vue'),
        meta: { title: '상품 속성 수정' },
        hidden: true,
      },
      {
        path: 'brand',
        name: 'brand',
        component: () => import('@/views/pms/brand/index.vue'),
        meta: { title: '브랜드 관리', icon: 'product-brand' },
      },
      {
        path: 'addBrand',
        name: 'addBrand',
        component: () => import('@/views/pms/brand/add.vue'),
        meta: { title: '브랜드 추가' },
        hidden: true,
      },
      {
        path: 'updateBrand',
        name: 'updateBrand',
        component: () => import('@/views/pms/brand/update.vue'),
        meta: { title: '브랜드 편집' },
        hidden: true,
      },
    ],
  },
  {
    path: '/oms',
    component: Layout,
    redirect: '/oms/order',
    name: 'oms',
    meta: { title: '주문', icon: 'order' },
    children: [
      {
        path: 'order',
        name: 'order',
        component: () => import('@/views/oms/order/index.vue'),
        meta: { title: '주문 목록', icon: 'product-list' },
      },
      {
        path: 'orderDetail',
        name: 'orderDetail',
        component: () => import('@/views/oms/order/orderDetail.vue'),
        meta: { title: '주문 상세' },
        hidden: true,
      },
      {
        path: 'deliverOrderList',
        name: 'deliverOrderList',
        component: () => import('@/views/oms/order/deliverOrderList.vue'),
        meta: { title: '발송 목록' },
        hidden: true,
      },
      {
        path: 'orderSetting',
        name: 'orderSetting',
        component: () => import('@/views/oms/order/setting.vue'),
        meta: { title: '주문 설정', icon: 'order-setting' },
      },
      {
        path: 'returnApply',
        name: 'returnApply',
        component: () => import('@/views/oms/apply/index.vue'),
        meta: { title: '반품 신청 처리', icon: 'order-return' },
      },
      {
        path: 'returnReason',
        name: 'returnReason',
        component: () => import('@/views/oms/apply/reason.vue'),
        meta: { title: '반품 사유 설정', icon: 'order-return-reason' },
      },
      {
        path: 'returnApplyDetail',
        name: 'returnApplyDetail',
        component: () => import('@/views/oms/apply/applyDetail.vue'),
        meta: { title: '반품 신청 상세' },
        hidden: true,
      },
    ],
  },
  {
    path: '/sms',
    component: Layout,
    redirect: '/sms/coupon',
    name: 'sms',
    meta: { title: '마케팅', icon: 'sms' },
    children: [
      {
        path: 'flash',
        name: 'flash',
        component: () => import('@/views/sms/flash/index.vue'),
        meta: { title: '타임 세일 이벤트 목록', icon: 'sms-flash' },
      },
      {
        path: 'flashSession',
        name: 'flashSession',
        component: () => import('@/views/sms/flash/sessionList.vue'),
        meta: { title: '타임 세일 시간대 목록' },
        hidden: true,
      },
      {
        path: 'selectSession',
        name: 'selectSession',
        component: () => import('@/views/sms/flash/selectSessionList.vue'),
        meta: { title: '타임 세일 시간대 선택' },
        hidden: true,
      },
      {
        path: 'flashProductRelation',
        name: 'flashProductRelation',
        component: () => import('@/views/sms/flash/productRelationList.vue'),
        meta: { title: '타임 세일 상품 목록' },
        hidden: true,
      },
      {
        path: 'coupon',
        name: 'coupon',
        component: () => import('@/views/sms/coupon/index.vue'),
        meta: { title: '쿠폰 목록', icon: 'sms-coupon' },
      },
      {
        path: 'addCoupon',
        name: 'addCoupon',
        component: () => import('@/views/sms/coupon/add.vue'),
        meta: { title: '쿠폰 추가' },
        hidden: true,
      },
      {
        path: 'updateCoupon',
        name: 'updateCoupon',
        component: () => import('@/views/sms/coupon/update.vue'),
        meta: { title: '쿠폰 수정' },
        hidden: true,
      },
      {
        path: 'couponHistory',
        name: 'couponHistory',
        component: () => import('@/views/sms/coupon/history.vue'),
        meta: { title: '쿠폰 수령 상세' },
        hidden: true,
      },
      {
        path: 'brand',
        name: 'homeBrand',
        component: () => import('@/views/sms/brand/index.vue'),
        meta: { title: '브랜드 추천', icon: 'product-brand' },
      },
      {
        path: 'new',
        name: 'homeNew',
        component: () => import('@/views/sms/new/index.vue'),
        meta: { title: '신상품 추천', icon: 'sms-new' },
      },
      {
        path: 'hot',
        name: 'homeHot',
        component: () => import('@/views/sms/hot/index.vue'),
        meta: { title: '인기 추천', icon: 'sms-hot' },
      },
      {
        path: 'subject',
        name: 'homeSubject',
        component: () => import('@/views/sms/subject/index.vue'),
        meta: { title: '기획전 추천', icon: 'sms-subject' },
      },
      {
        path: 'advertise',
        name: 'homeAdvertise',
        component: () => import('@/views/sms/advertise/index.vue'),
        meta: { title: '광고 목록', icon: 'sms-ad' },
      },
      {
        path: 'addAdvertise',
        name: 'addHomeAdvertise',
        component: () => import('@/views/sms/advertise/add.vue'),
        meta: { title: '광고 추가' },
        hidden: true,
      },
      {
        path: 'updateAdvertise',
        name: 'updateHomeAdvertise',
        component: () => import('@/views/sms/advertise/update.vue'),
        meta: { title: '광고 편집' },
        hidden: true,
      },
    ],
  },
  {
    path: '/ums',
    component: Layout,
    redirect: '/ums/admin',
    name: 'ums',
    meta: { title: '권한', icon: 'ums' },
    children: [
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/ums/admin/index.vue'),
        meta: { title: '사용자 목록', icon: 'ums-admin' },
      },
      {
        path: 'role',
        name: 'role',
        component: () => import('@/views/ums/role/index.vue'),
        meta: { title: '역할 목록', icon: 'ums-role' },
      },
      {
        path: 'allocMenu',
        name: 'allocMenu',
        component: () => import('@/views/ums/role/allocMenu.vue'),
        meta: { title: '메뉴 할당' },
        hidden: true,
      },
      {
        path: 'allocResource',
        name: 'allocResource',
        component: () => import('@/views/ums/role/allocResource.vue'),
        meta: { title: '리소스 할당' },
        hidden: true,
      },
      {
        path: 'menu',
        name: 'menu',
        component: () => import('@/views/ums/menu/index.vue'),
        meta: { title: '메뉴 목록', icon: 'ums-menu' },
      },
      {
        path: 'addMenu',
        name: 'addMenu',
        component: () => import('@/views/ums/menu/add.vue'),
        meta: { title: '메뉴 추가' },
        hidden: true,
      },
      {
        path: 'updateMenu',
        name: 'updateMenu',
        component: () => import('@/views/ums/menu/update.vue'),
        meta: { title: '메뉴 수정' },
        hidden: true,
      },
      {
        path: 'resource',
        name: 'resource',
        component: () => import('@/views/ums/resource/index.vue'),
        meta: { title: '리소스 목록', icon: 'ums-resource' },
      },
      {
        path: 'resourceCategory',
        name: 'resourceCategory',
        component: () => import('@/views/ums/resource/categoryList.vue'),
        meta: { title: '리소스 카테고리' },
        hidden: true,
      },
    ],
  },
]

// createWebHistory(History 모드) 주소 형식(서버 설정 필요):http://domain.com/admin/home
// createWebHashHistory(Hash 모드) 주소 형식(# 기호가 하나 더 붙음):http://domain.com/admin/#/home
const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRouterMap,
})

export default router

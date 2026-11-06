package com.macro.mall.service;

import com.macro.mall.model.SmsCouponHistory;

import java.util.List;

/**
 * 쿠폰 수령 기록 관리Service
 * Created by macro on 2018/11/6.
 */
public interface SmsCouponHistoryService {
    /**
     * 쿠폰 수령 기록 페이지 조회
     * @param couponId 쿠폰id
     * @param useStatus 사용 상태
     * @param orderSn 주문 번호 사용
     */
    List<SmsCouponHistory> list(Long couponId, Integer useStatus, String orderSn, Integer pageSize, Integer pageNum);
}

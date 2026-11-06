package com.macro.mall.service;

import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.model.SmsCoupon;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 관리Service
 * Created by macro on 2018/8/28.
 */
public interface SmsCouponService {
    /**
     * 쿠폰 추가
     */
    @Transactional
    int create(SmsCouponParam couponParam);

    /**
     * 쿠폰 id로 쿠폰 삭제
     */
    @Transactional
    int delete(Long id);

    /**
     * 쿠폰 id로 쿠폰 정보 수정
     */
    @Transactional
    int update(Long id, SmsCouponParam couponParam);

    /**
     * 쿠폰 목록 페이지 조회
     */
    List<SmsCoupon> list(String name, Integer type, Integer pageSize, Integer pageNum);

    /**
     * 쿠폰 상세 조회
     * @param id 쿠폰 테이블id
     */
    SmsCouponParam getItem(Long id);
}

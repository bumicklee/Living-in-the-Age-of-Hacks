package com.macro.mall.portal.dao;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 쿠폰 수령 기록 관리 커스텀Dao
 * Created by macro on 2018/8/29.
 */
public interface SmsCouponHistoryDao {
    /**
     * 쿠폰 내역 상세 조회
     */
    List<SmsCouponHistoryDetail> getDetailList(@Param("memberId") Long memberId);

    /**
     * 지정한 회원의 쿠폰 목록 조회
     */
    List<SmsCoupon> getCouponList(@Param("memberId") Long memberId, @Param("useStatus")Integer useStatus);
}

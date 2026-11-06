package com.macro.mall.dao;

import com.macro.mall.dto.SmsCouponParam;
import org.apache.ibatis.annotations.Param;

/**
 * 쿠폰 관리 커스텀Dao
 * Created by macro on 2018/8/29.
 */
public interface SmsCouponDao {
    /**
     * 바인딩 관계를 포함한 쿠폰 상세 조회
     */
    SmsCouponParam getItem(@Param("id") Long id);
}

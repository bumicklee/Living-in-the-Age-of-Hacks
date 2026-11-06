package com.macro.mall.dao;

import com.macro.mall.model.SmsCouponProductRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 쿠폰과 상품 관계 커스텀Dao
 * Created by macro on 2018/8/28.
 */
public interface SmsCouponProductRelationDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list")List<SmsCouponProductRelation> productRelationList);
}

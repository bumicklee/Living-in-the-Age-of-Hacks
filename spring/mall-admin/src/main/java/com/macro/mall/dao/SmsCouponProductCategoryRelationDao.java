package com.macro.mall.dao;

import com.macro.mall.model.SmsCouponProductCategoryRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 쿠폰과 상품 카테고리 관계 관리 커스텀Dao
 * Created by macro on 2018/8/28.
 */
public interface SmsCouponProductCategoryRelationDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list")List<SmsCouponProductCategoryRelation> productCategoryRelationList);
}

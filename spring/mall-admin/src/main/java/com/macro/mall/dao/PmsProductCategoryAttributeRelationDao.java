package com.macro.mall.dao;

import com.macro.mall.model.PmsProductCategoryAttributeRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 상품 카테고리-속성 관계 커스텀Dao
 * Created by macro on 2018/5/23.
 */
public interface PmsProductCategoryAttributeRelationDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<PmsProductCategoryAttributeRelation> productCategoryAttributeRelationList);
}

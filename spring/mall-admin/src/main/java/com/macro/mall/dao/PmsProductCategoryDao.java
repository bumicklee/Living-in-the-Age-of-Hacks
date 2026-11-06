package com.macro.mall.dao;

import com.macro.mall.dto.PmsProductCategoryWithChildrenItem;

import java.util.List;

/**
 * 상품 카테고리 커스텀Dao
 * Created by macro on 2018/5/25.
 */
public interface PmsProductCategoryDao {
    /**
     * 상품 카테고리와 하위 카테고리 조회
     */
    List<PmsProductCategoryWithChildrenItem> listWithChildren();
}

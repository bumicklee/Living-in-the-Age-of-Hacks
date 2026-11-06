package com.macro.mall.dao;

import com.macro.mall.dto.PmsProductAttributeCategoryItem;

import java.util.List;

/**
 * 상품 속성 카테고리 관리 커스텀Dao
 * Created by macro on 2018/5/24.
 */
public interface PmsProductAttributeCategoryDao {
    /**
     * 속성을 포함한 상품 속성 카테고리 조회
     */
    List<PmsProductAttributeCategoryItem> getListWithAttr();
}

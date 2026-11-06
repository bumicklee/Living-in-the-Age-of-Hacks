package com.macro.mall.portal.service;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;

import java.util.List;

/**
 * 프런트 상품 관리Service
 * Created by macro on 2020/4/6.
 */
public interface PmsPortalProductService {
    /**
     * 통합 상품 검색
     */
    List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort);

    /**
     * 트리 구조로 모든 상품 카테고리 조회
     */
    List<PmsProductCategoryNode> categoryTreeList();

    /**
     * 프런트 상품 상세 조회
     */
    PmsPortalProductDetail detail(Long id);
}

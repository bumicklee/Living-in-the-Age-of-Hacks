package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;

import java.util.List;

/**
 * 프런트 브랜드 관리Service
 * Created by macro on 2020/5/15.
 */
public interface PmsPortalBrandService {
    /**
     * 추천 브랜드 페이지 조회
     */
    List<PmsBrand> recommendList(Integer pageNum, Integer pageSize);

    /**
     * 브랜드 상세 조회
     */
    PmsBrand detail(Long brandId);

    /**
     * 브랜드 연관 상품 페이지 조회
     */
    CommonPage<PmsProduct> productList(Long brandId, Integer pageNum, Integer pageSize);
}

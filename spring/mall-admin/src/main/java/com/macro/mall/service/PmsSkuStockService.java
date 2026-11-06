package com.macro.mall.service;

import com.macro.mall.model.PmsSkuStock;

import java.util.List;

/**
 * 상품 SKU 재고 관리Service
 * Created by macro on 2018/4/27.
 */
public interface PmsSkuStockService {
    /**
     * 상품 id와 skuCode 키워드로 모호 검색
     */
    List<PmsSkuStock> getList(Long pid, String keyword);

    /**
     * 상품 재고 정보 일괄 업데이트
     */
    int update(Long pid, List<PmsSkuStock> skuStockList);
}

package com.macro.mall.portal.service;

import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductCategory;
import com.macro.mall.portal.domain.HomeContentResult;

import java.util.List;

/**
 * 홈 콘텐츠 관리Service
 * Created by macro on 2019/1/28.
 */
public interface HomeService {

    /**
     * 홈 콘텐츠 조회
     */
    HomeContentResult content();

    /**
     * 홈 상품 추천
     */
    List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum);

    /**
     * 상품 카테고리 조회
     * @param parentId 0:1차 카테고리 조회; 그 외: 지정한 2차 카테고리 조회
     */
    List<PmsProductCategory> getProductCateList(Long parentId);

    /**
     * 기획전 카테고리로 기획전 페이징 조회
     * @param cateId 기획전 카테고리id
     */
    List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum);

    /**
     * 인기 추천 상품 페이지 조회
     */
    List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize);

    /**
     * 신상품 추천 상품 페이지 조회
     */
    List<PmsProduct> newProductList(Integer pageNum, Integer pageSize);
}

package com.macro.mall.service;

import com.macro.mall.dto.PmsProductCategoryParam;
import com.macro.mall.dto.PmsProductCategoryWithChildrenItem;
import com.macro.mall.model.PmsProductCategory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 카테고리 관리Service
 * Created by macro on 2018/4/26.
 */
public interface PmsProductCategoryService {
    /**
     * 상품 카테고리 생성
     */
    @Transactional
    int create(PmsProductCategoryParam pmsProductCategoryParam);

    /**
     * 상품 카테고리 수정
     */
    @Transactional
    int update(Long id, PmsProductCategoryParam pmsProductCategoryParam);

    /**
     * 상품 카테고리 페이지 조회
     */
    List<PmsProductCategory> getList(Long parentId, Integer pageSize, Integer pageNum);

    /**
     * 상품 카테고리 삭제
     */
    int delete(Long id);

    /**
     * ID로 상품 카테고리 조회
     */
    PmsProductCategory getItem(Long id);

    /**
     * 내비게이션 상태 일괄 수정
     */
    int updateNavStatus(List<Long> ids, Integer navStatus);

    /**
     * 표시 상태 일괄 수정
     */
    int updateShowStatus(List<Long> ids, Integer showStatus);

    /**
     * 계층 구조로 상품 카테고리 조회
     */
    List<PmsProductCategoryWithChildrenItem> listWithChildren();
}

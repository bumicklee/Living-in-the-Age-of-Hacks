package com.macro.mall.service;

import com.macro.mall.dto.PmsProductAttributeCategoryItem;
import com.macro.mall.model.PmsProductAttributeCategory;

import java.util.List;

/**
 * 상품 속성 카테고리 관리Service
 * Created by macro on 2018/4/26.
 */
public interface PmsProductAttributeCategoryService {
    /**
     * 속성 카테고리 생성
     */
    int create(String name);

    /**
     * 속성 카테고리 수정
     */
    int update(Long id, String name);

    /**
     * 속성 카테고리 삭제
     */
    int delete(Long id);

    /**
     * 속성 카테고리 상세 조회
     */
    PmsProductAttributeCategory getItem(Long id);

    /**
     * 속성 카테고리 페이지 조회
     */
    List<PmsProductAttributeCategory> getList(Integer pageSize, Integer pageNum);

    /**
     * 속성을 포함한 속성 카테고리 조회
     */
    List<PmsProductAttributeCategoryItem> getListWithAttr();
}

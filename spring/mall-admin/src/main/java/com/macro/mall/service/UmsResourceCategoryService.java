package com.macro.mall.service;

import com.macro.mall.model.UmsResourceCategory;

import java.util.List;

/**
 * 관리자 리소스 카테고리 관리Service
 * Created by macro on 2020/2/5.
 */
public interface UmsResourceCategoryService {

    /**
     * 전체 리소스 카테고리 조회
     */
    List<UmsResourceCategory> listAll();

    /**
     * 리소스 카테고리 생성
     */
    int create(UmsResourceCategory umsResourceCategory);

    /**
     * 리소스 카테고리 수정
     */
    int update(Long id, UmsResourceCategory umsResourceCategory);

    /**
     * 리소스 카테고리 삭제
     */
    int delete(Long id);
}

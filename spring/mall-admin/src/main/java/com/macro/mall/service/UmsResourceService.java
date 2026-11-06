package com.macro.mall.service;

import com.macro.mall.model.UmsResource;

import java.util.List;

/**
 * 관리자 리소스 관리Service
 * Created by macro on 2020/2/2.
 */
public interface UmsResourceService {
    /**
     * 리소스 추가
     */
    int create(UmsResource umsResource);

    /**
     * 리소스 수정
     */
    int update(Long id, UmsResource umsResource);

    /**
     * 리소스 상세 조회
     */
    UmsResource getItem(Long id);

    /**
     * 리소스 삭제
     */
    int delete(Long id);

    /**
     * 리소스 페이지 조회
     */
    List<UmsResource> list(Long categoryId, String nameKeyword, String urlKeyword, Integer pageSize, Integer pageNum);

    /**
     * 전체 리소스 조회
     */
    List<UmsResource> listAll();
}

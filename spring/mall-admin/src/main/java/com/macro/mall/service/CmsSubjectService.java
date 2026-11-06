package com.macro.mall.service;

import com.macro.mall.model.CmsSubject;

import java.util.List;

/**
 * 상품 기획전 관리Service
 * Created by macro on 2018/6/1.
 */
public interface CmsSubjectService {
    /**
     * 모든 기획전 조회
     */
    List<CmsSubject> listAll();

    /**
     * 기획전 페이지 조회
     */
    List<CmsSubject> list(String keyword, Integer pageNum, Integer pageSize);
}

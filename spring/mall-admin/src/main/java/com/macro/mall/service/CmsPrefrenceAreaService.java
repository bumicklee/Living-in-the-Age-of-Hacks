package com.macro.mall.service;

import com.macro.mall.model.CmsPrefrenceArea;

import java.util.List;

/**
 * 엄선 전용관 관리Service
 * Created by macro on 2018/6/1.
 */
public interface CmsPrefrenceAreaService {
    /**
     * 전체 엄선 상품 코너 조회
     */
    List<CmsPrefrenceArea> listAll();
}

package com.macro.mall.service;

import com.macro.mall.model.UmsMemberLevel;

import java.util.List;

/**
 * 회원 등급 관리Service
 * Created by macro on 2018/4/26.
 */
public interface UmsMemberLevelService {
    /**
     * 전체 회원 등급 조회
     * @param defaultStatus 기본 회원 여부
     */
    List<UmsMemberLevel> list(Integer defaultStatus);
}

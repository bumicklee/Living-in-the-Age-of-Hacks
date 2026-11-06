package com.macro.mall.service;

import com.macro.mall.dto.UmsMenuNode;
import com.macro.mall.model.UmsMenu;

import java.util.List;

/**
 * 관리자 메뉴 관리Service
 * Created by macro on 2020/2/2.
 */
public interface UmsMenuService {
    /**
     * 관리자 메뉴 생성
     */
    int create(UmsMenu umsMenu);

    /**
     * 관리자 메뉴 수정
     */
    int update(Long id, UmsMenu umsMenu);

    /**
     * ID로 메뉴 상세 조회
     */
    UmsMenu getItem(Long id);

    /**
     * ID로 메뉴 삭제
     */
    int delete(Long id);

    /**
     * 관리자 메뉴 페이지 조회
     */
    List<UmsMenu> list(Long parentId, Integer pageSize, Integer pageNum);

    /**
     * 모든 메뉴 목록을 트리 구조로 반환
     */
    List<UmsMenuNode> treeList();

    /**
     * 메뉴 표시 상태 수정
     */
    int updateHidden(Long id, Integer hidden);
}

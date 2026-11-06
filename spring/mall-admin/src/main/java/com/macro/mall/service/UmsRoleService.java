package com.macro.mall.service;

import com.macro.mall.model.UmsMenu;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 역할 관리Service
 * Created by macro on 2018/9/30.
 */
public interface UmsRoleService {
    /**
     * 역할 추가
     */
    int create(UmsRole role);

    /**
     * 역할 정보 수정
     */
    int update(Long id, UmsRole role);

    /**
     * 역할 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 전체 역할 목록 조회
     */
    List<UmsRole> list();

    /**
     * 역할 목록 페이지 조회
     */
    List<UmsRole> list(String keyword, Integer pageSize, Integer pageNum);

    /**
     * 관리자 ID로 해당 메뉴 조회
     */
    List<UmsMenu> getMenuList(Long adminId);

    /**
     * 역할 관련 메뉴 조회
     */
    List<UmsMenu> listMenu(Long roleId);

    /**
     * 역할 관련 리소스 조회
     */
    List<UmsResource> listResource(Long roleId);

    /**
     * 역할에 메뉴 할당
     */
    @Transactional
    int allocMenu(Long roleId, List<Long> menuIds);

    /**
     * 역할에 리소스 할당
     */
    @Transactional
    int allocResource(Long roleId, List<Long> resourceIds);
}

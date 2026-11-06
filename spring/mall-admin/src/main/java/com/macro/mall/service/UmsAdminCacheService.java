package com.macro.mall.service;

import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsResource;

import java.util.List;

/**
 * 관리자 사용자 캐시 관리Service
 * Created by macro on 2020/3/13.
 */
public interface UmsAdminCacheService {
    /**
     * 관리자 사용자 캐시 삭제
     */
    void delAdmin(Long adminId);

    /**
     * 관리자 사용자 리소스 목록 캐시 삭제
     */
    void delResourceList(Long adminId);

    /**
     * 역할 관련 리소스 정보가 변경되면 관련 관리자 캐시 삭제
     */
    void delResourceListByRole(Long roleId);

    /**
     * 역할 관련 리소스 정보가 변경되면 관련 관리자 캐시 삭제
     */
    void delResourceListByRoleIds(List<Long> roleIds);

    /**
     * 리소스 정보가 변경되면 해당 리소스의 관리자 캐시 삭제
     */
    void delResourceListByResource(Long resourceId);

    /**
     * 캐시된 관리자 사용자 정보 조회
     */
    UmsAdmin getAdmin(String username);

    /**
     * 관리자 사용자 정보 캐시 설정
     */
    void setAdmin(UmsAdmin admin);

    /**
     * 캐시된 관리자 사용자 리소스 목록 조회
     */
    List<UmsResource> getResourceList(Long adminId);

    /**
     * 관리자 사용자 리소스 목록 캐시 설정
     */
    void setResourceList(Long adminId, List<UmsResource> resourceList);
}

package com.macro.mall.dao;

import com.macro.mall.model.UmsAdminRoleRelation;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 관리자 사용자-역할 관계 관리 커스텀Dao
 * Created by macro on 2018/10/8.
 */
public interface UmsAdminRoleRelationDao {
    /**
     * 사용자-역할 관계 일괄 삽입
     */
    int insertList(@Param("list") List<UmsAdminRoleRelation> adminRoleRelationList);

    /**
     * 전체 역할 조회
     */
    List<UmsRole> getRoleList(@Param("adminId") Long adminId);

    /**
     * 사용자가 접근 가능한 전체 리소스 조회
     */
    List<UmsResource> getResourceList(@Param("adminId") Long adminId);

    /**
     * 리소스 관련 사용자 ID 목록 조회
     */
    List<Long> getAdminIdList(@Param("resourceId") Long resourceId);
}

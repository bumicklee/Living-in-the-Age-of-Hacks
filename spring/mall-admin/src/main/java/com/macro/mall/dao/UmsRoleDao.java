package com.macro.mall.dao;

import com.macro.mall.model.UmsMenu;
import com.macro.mall.model.UmsResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 관리자 역할 관리 커스텀Dao
 * Created by macro on 2020/2/2.
 */
public interface UmsRoleDao {
    /**
     * 관리자 사용자 ID로 메뉴 조회
     */
    List<UmsMenu> getMenuList(@Param("adminId") Long adminId);
    /**
     * 역할 ID로 메뉴 조회
     */
    List<UmsMenu> getMenuListByRoleId(@Param("roleId") Long roleId);
    /**
     * 역할 ID로 리소스 조회
     */
    List<UmsResource> getResourceListByRoleId(@Param("roleId") Long roleId);
}

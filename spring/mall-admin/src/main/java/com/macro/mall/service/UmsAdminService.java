package com.macro.mall.service;

import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 사용자 관리Service
 * Created by macro on 2018/4/26.
 */
public interface UmsAdminService {
    /**
     * 사용자 이름으로 관리자 조회
     */
    UmsAdmin getAdminByUsername(String username);

    /**
     * 회원가입 기능
     */
    UmsAdmin register(UmsAdminParam umsAdminParam);

    /**
     * 로그인 기능
     * @param username 사용자 이름
     * @param password 비밀번호
     * @return 생성된 JWT의token
     */
    String login(String username,String password);

    /**
     * token 갱신 기능
     * @param oldToken 기존token
     */
    String refreshToken(String oldToken);

    /**
     * 사용자 id로 사용자 조회
     */
    UmsAdmin getItem(Long id);

    /**
     * 사용자 이름 또는 닉네임으로 사용자 페이징 조회
     */
    List<UmsAdmin> list(String keyword, Integer pageSize, Integer pageNum);

    /**
     * 지정한 사용자 정보 수정
     */
    int update(Long id, UmsAdmin admin);

    /**
     * 지정한 사용자 삭제
     */
    int delete(Long id);

    /**
     * 사용자 역할 관계 수정
     */
    @Transactional
    int updateRole(Long adminId, List<Long> roleIds);

    /**
     * 사용자에 해당하는 역할 조회
     */
    List<UmsRole> getRoleList(Long adminId);

    /**
     * 지정한 사용자가 접근 가능한 리소스 조회
     */
    List<UmsResource> getResourceList(Long adminId);

    /**
     * 비밀번호 변경
     */
    int updatePassword(UpdateAdminPasswordParam updatePasswordParam);

    /**
     * 사용자 정보 조회
     */
    UserDetails loadUserByUsername(String username);

    /**
     * 캐시 서비스 조회
     */
    UmsAdminCacheService getCacheService();

    /**
     * 로그아웃 기능
     * @param username 사용자 이름
     */
    void logout(String username);
}

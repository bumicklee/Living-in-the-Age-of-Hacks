package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관리Service
 * Created by macro on 2018/8/3.
 */
public interface UmsMemberService {
    /**
     * 사용자 이름으로 회원 조회
     */
    UmsMember getByUsername(String username);

    /**
     * 회원 번호로 회원 조회
     */
    UmsMember getById(Long id);

    /**
     * 사용자 회원가입
     */
    @Transactional
    void register(String username, String password, String telephone, String authCode);

    /**
     * 인증 코드 생성
     */
    String generateAuthCode(String telephone);

    /**
     * 비밀번호 변경
     */
    @Transactional
    void updatePassword(String telephone, String password, String authCode);

    /**
     * 현재 로그인한 회원 조회
     */
    UmsMember getCurrentMember();

    /**
     * 회원 id로 회원 포인트 수정
     */
    void updateIntegration(Long id,Integer integration);


    /**
     * [교육용 추가] 로그인한 회원의 정보 수정
     * ✏️ 2-4: 무엇을 받는지 정한다(다온마켓 교육용 표시)
     */
    void updateInfo(UmsMember member);

    /**
     * 사용자 정보 조회
     */
    UserDetails loadUserByUsername(String username);

    /**
     * 로그인 후 조회token
     */
    String login(String username, String password);

    /**
     * 새로고침token
     */
    String refreshToken(String token);
}

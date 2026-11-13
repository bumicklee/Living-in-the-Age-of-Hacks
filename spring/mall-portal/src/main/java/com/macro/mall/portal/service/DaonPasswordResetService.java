package com.macro.mall.portal.service;

/**
 * [교육용 추가] 다온마켓 「이메일로 재설정 링크 받기」(수업 4-5, T11). 원본 Spring에는 메일 링크 재설정이 없다.
 * 원본의 문자 인증 재설정(/sso/getAuthCode → /sso/updatePassword)과 따로 있다.
 */
public interface DaonPasswordResetService {
    /** 그 아이디의 랩 메일 주소로 재설정 링크를 보낸다. 없는 아이디여도 같은 응답이다 */
    void sendLink(String username);

    /** 링크 속 토큰으로 새 비밀번호를 정한다 */
    void reset(String token, String password);
}

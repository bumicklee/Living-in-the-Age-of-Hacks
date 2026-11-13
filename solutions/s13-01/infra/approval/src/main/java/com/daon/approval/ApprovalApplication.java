package com.daon.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * [다온마켓 A10 · 13장] 승인 서비스.
 *
 * <pre>
 * 관리자 앱 ──요청(반품 ID·요청 금액, 관리자 토큰)──▶ 승인 서비스 ──읽기──▶ DB(원장)
 *                                                      │ 정책(policy.yml)으로 판단
 *                                                      ▼
 *                       서명된 승인 토큰(반품 ID·금액·요청자·승인자·1회용 번호·만료) ── 금고 transit daon-approval
 *                                                      ▼
 * 관리자 앱의 환불 실행기 ──확인(transit verify)── 맞을 때만 결제사(가짜) 환불
 * </pre>
 *
 * 승인 서비스는 환불을 직접 실행하지 않는다(결정 #17). 서명 키는 이 서비스만 쓴다. 관리자 앱은 확인만 할 수 있다.
 */
@SpringBootApplication
@EnableScheduling
public class ApprovalApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}

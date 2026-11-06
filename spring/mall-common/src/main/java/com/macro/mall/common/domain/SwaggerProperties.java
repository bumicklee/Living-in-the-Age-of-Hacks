package com.macro.mall.common.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Swagger사용자 정의 설정
 * Created by macro on 2020/7/16.
 */
@Data
@EqualsAndHashCode
@Builder
public class SwaggerProperties {
    /**
     * API문서 생성 기본 경로
     */
    private String apiBasePackage;
    /**
     * 로그인 인증을 사용할까요?
     */
    private boolean enableSecurity;
    /**
     * 문서 제목
     */
    private String title;
    /**
     * 문서 설명
     */
    private String description;
    /**
     * 문서 버전
     */
    private String version;
    /**
     * 문서 담당자 이름
     */
    private String contactName;
    /**
     * 문서 담당자 웹사이트
     */
    private String contactUrl;
    /**
     * 문서 담당자 이메일
     */
    private String contactEmail;
}

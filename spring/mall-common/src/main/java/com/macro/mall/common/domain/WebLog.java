package com.macro.mall.common.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Controller계층의 로그 래핑 클래스
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class WebLog {
    /**
     * 작업 설명
     */
    private String description;

    /**
     * 작업 사용자
     */
    private String username;

    /**
     * 작업 시간
     */
    private Long startTime;

    /**
     * 소요 시간
     */
    private Integer spendTime;

    /**
     * 루트 경로
     */
    private String basePath;

    /**
     * URI
     */
    private String uri;

    /**
     * URL
     */
    private String url;

    /**
     * 요청 유형
     */
    private String method;

    /**
     * IP주소
     */
    private String ip;

    /**
     * 요청 파라미터
     */
    private Object parameter;

    /**
     * 반환 결과
     */
    private Object result;

}

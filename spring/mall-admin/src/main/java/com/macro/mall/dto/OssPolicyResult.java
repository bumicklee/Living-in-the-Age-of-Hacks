package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OSS 파일 업로드 인증 반환 결과 조회
 * Created by macro on 2018/5/17.
 */
@Data
@EqualsAndHashCode
public class OssPolicyResult {
    @Schema(title = "접근 인증에 사용되는 사용자 식별자")
    private String accessKeyId;
    @Schema(title = "사용자 폼 업로드 정책, base64로 인코딩된 문자열")
    private String policy;
    @Schema(title = "policy에 서명한 문자열")
    private String signature;
    @Schema(title = "업로드 폴더 경로 접두사")
    private String dir;
    @Schema(title = "oss외부 서비스 접근 도메인")
    private String host;
    @Schema(title = "업로드 성공 후 콜백 설정")
    private String callback;
}

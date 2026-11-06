package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OSS업로드 성공 후 콜백 파라미터
 * Created by macro on 2018/5/17.
 */
@Data
@EqualsAndHashCode
public class OssCallbackParam {
    @Schema(title = "요청 콜백 주소")
    private String callbackUrl;
    @Schema(title = "콜백 시 request에 전달되는 파라미터")
    private String callbackBody;
    @Schema(title = "콜백 시 전달되는 파라미터 형식, 예: 폼 제출 형태")
    private String callbackBodyType;
}

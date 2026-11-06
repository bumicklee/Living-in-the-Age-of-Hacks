package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OSS업로드 파일 콜백 결과
 * Created by macro on 2018/5/17.
 */
@Data
@EqualsAndHashCode
public class OssCallbackResult {
    @Schema(title = "파일 이름")
    private String filename;
    @Schema(title = "파일 크기")
    private String size;
    @Schema(title = "파일의mimeType")
    private String mimeType;
    @Schema(title = "이미지 파일 너비")
    private String width;
    @Schema(title = "이미지 파일 높이")
    private String height;
}

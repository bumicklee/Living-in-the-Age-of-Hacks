package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 파일 업로드 반환 결과
 * Created by macro on 2019/12/25.
 */
@Data
@EqualsAndHashCode
public class MinioUploadDto {
    @Schema(title = "파일 접근URL")
    private String url;
    @Schema(title = "파일 이름")
    private String name;
}

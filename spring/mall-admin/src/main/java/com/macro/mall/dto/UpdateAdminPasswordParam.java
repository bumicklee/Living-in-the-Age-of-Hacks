package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotEmpty;

/**
 * 사용자 이름·비밀번호 수정 파라미터
 * Created by macro on 2019/10/9.
 */
@Getter
@Setter
public class UpdateAdminPasswordParam {
    @NotEmpty
    @Schema(title =  "사용자 이름", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @NotEmpty
    @Schema(title =  "기존 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;
    @NotEmpty
    @Schema(title =  "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}

package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

/**
 * 사용자 회원가입 파라미터
 * Created by macro on 2018/4/26.
 */
@Getter
@Setter
public class UmsAdminParam {
    @NotEmpty
    @Schema(title = "사용자 이름", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @NotEmpty
    @Schema(title =  "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
    @Schema(title =  "사용자 프로필 사진")
    private String icon;
    @Email
    @Schema(title =  "이메일")
    private String email;
    @Schema(title =  "사용자 닉네임")
    private String nickName;
    @Schema(title =  "메모")
    private String note;
}

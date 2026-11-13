package com.macro.mall.portal.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 다온마켓 2-4: 회원정보 수정 요청 전용 DTO.
 * 회원이 스스로 고칠 수 있는 항목만 둔다. 등급(memberLevelId)·포인트(integration)·성장치(growth)·상태(status)는 없다.
 * DTO에 없는 항목이 본문에 들어오면 저장하지 않고, 이름만 ignoredFields에 모아 ⑤ 탐지 로그에 쓴다(값은 남기지 않는다).
 */
@Getter
@Setter
public class UmsMemberUpdateParam {
    @Size(max = 64)
    @Schema(title = "닉네임")
    private String nickname;
    @Min(0) @Max(2)
    @Schema(title = "성별: 0 미정, 1 남, 2 여")
    private Integer gender;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    @Schema(title = "생일")
    private Date birthday;
    @Size(max = 64)
    @Schema(title = "사는 도시")
    private String city;
    @Size(max = 100)
    @Schema(title = "직업")
    private String job;
    @Size(max = 200)
    @Schema(title = "한 줄 소개")
    private String personalizedSignature;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> ignoredFields = new TreeSet<>();

    /** [랩 스위치] secure-kit.request-dto.enabled=false일 때만 쓴다: 고치기 전 모습을 다시 보여 주려고 값을 잠깐 들고 있는다(로그에는 남기지 않는다) */
    @JsonIgnore
    @Schema(hidden = true)
    private final Map<String, Object> ignoredValues = new LinkedHashMap<>();

    @JsonAnySetter
    public void ignore(String name, Object value) {
        if (ignoredFields.size() < 20) {
            String n = name.length() > 40 ? name.substring(0, 40) : name;
            ignoredFields.add(n);
            ignoredValues.put(n, value);
        }
    }
}

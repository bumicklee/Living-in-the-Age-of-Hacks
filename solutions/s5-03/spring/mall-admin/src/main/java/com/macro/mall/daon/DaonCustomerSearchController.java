package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [교육용 추가 · 수업 5-3] 고객센터: 전화번호로 회원 찾기. 슈퍼 관리자만(/daon/customer/** 권한).
 * 평문 전화번호와 비교한다. 5-3에서 칸을 잠그면 암호문과 비교하므로 찾지 못한다(결과 0건). 5-4에서 검색용 값으로 고친다.
 */
@RestController
@Tag(name = "DaonCustomerSearchController", description = "다온마켓 고객 찾기(교육용 추가)")
@RequestMapping("/daon/customer")
public class DaonCustomerSearchController {
    @Autowired
    private UmsMemberMapper memberMapper;

    @Operation(summary = "전화번호로 회원 찾기")
    @GetMapping("/search")
    public CommonResult<List<Map<String, Object>>> search(@RequestParam String phone) {
        // ✏️ 5-4: 평문 전화번호와 비교한다. 칸이 잠기면 암호문과 비교해 0건이 된다(다온마켓 교육용 표시)
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andPhoneEqualTo(phone);
        return CommonResult.success(memberMapper.selectByExample(example).stream().map(DaonCustomerSearchController::row).collect(Collectors.toList()));
    }

    private static Map<String, Object> row(UmsMember m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("username", m.getUsername());
        r.put("nickname", m.getNickname());
        r.put("phone", m.getPhone());
        return r;
    }
}

package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.audit.Audited;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.security.crypto.PiiMask;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [교육용 추가] 다온마켓 고객센터 기능: 회원 목록(이름·전화·포인트).
 * 슈퍼 관리자만 쓰라고 만든 기능인데, 권한표(ums_resource)에 등록하지 않았다. 수업 2-3(T02)에서 다룬다.
 */
@RestController
@Tag(name = "DaonCustomerController", description = "다온마켓 고객 관리(교육용 추가)")
@RequestMapping("/daon/customer")
public class DaonCustomerController {
    @Autowired
    private UmsMemberMapper memberMapper;

    @Operation(summary = "회원 목록(고객센터)")
    @GetMapping("/list")
    @Audited(action = "customer.list", resource = "member")   // 다온마켓 8-3: 누가·몇 명을·왜 봤는지 감사 로그로
    public CommonResult<List<Map<String, Object>>> list() {
        UmsMemberExample example = new UmsMemberExample();
        example.setOrderByClause("id asc");
        List<Map<String, Object>> rows = memberMapper.selectByExample(example).stream()
                .map(DaonCustomerController::row)
                .collect(Collectors.toList());
        return CommonResult.success(rows);
    }

    private static Map<String, Object> row(UmsMember m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("username", m.getUsername());
        r.put("nickname", m.getNickname());
        // 다온마켓 5-4: 목록에는 가린 번호만. 원문은 사유를 적고 한 건씩 보는 길(DaonPiiRevealController)로만
        r.put("phone", SecureKitSwitch.on("pii-mask") ? PiiMask.phone(m.getPhone()) : m.getPhone());
        r.put("integration", m.getIntegration());
        return r;
    }
}

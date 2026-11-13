package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.security.crypto.PhoneIndex;
import com.macro.mall.security.crypto.PiiMask;
import com.macro.mall.security.securekit.SecureKitSwitch;
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
 * [다온마켓 5-4 정답] 고객센터: 전화번호로 회원 찾기. 검색용 값(키를 쓴 HMAC)으로 찾고, 결과 번호는 가려서 돌려준다.
 * 스위치 pii-mask를 끄면 5-3 완료 상태(평문 비교, 0건)로 돌아간다.
 */
@RestController
@Tag(name = "DaonCustomerSearchController", description = "다온마켓 고객 찾기(교육용 추가)")
@RequestMapping("/daon/customer")
public class DaonCustomerSearchController {
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private PhoneIndex phoneIndex;

    @Operation(summary = "전화번호로 회원 찾기")
    @GetMapping("/search")
    public CommonResult<List<Map<String, Object>>> search(@RequestParam String phone) {
        List<UmsMember> found;
        if (SecureKitSwitch.on("pii-mask")) {
            found = phoneIndex.findMemberId(phone).map(id -> List.of(memberMapper.selectByPrimaryKey(id))).orElse(List.of());
        } else {
            UmsMemberExample example = new UmsMemberExample();
            example.createCriteria().andPhoneEqualTo(phone);
            found = memberMapper.selectByExample(example);
        }
        return CommonResult.success(found.stream().map(DaonCustomerSearchController::row).collect(Collectors.toList()));
    }

    private static Map<String, Object> row(UmsMember m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("username", m.getUsername());
        r.put("nickname", m.getNickname());
        r.put("phone", SecureKitSwitch.on("pii-mask") ? PiiMask.phone(m.getPhone()) : m.getPhone());
        return r;
    }
}

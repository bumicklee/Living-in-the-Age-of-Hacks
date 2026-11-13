package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.mapper.PmsCommentMapper;
import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsCommentExample;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.github.pagehelper.PageHelper;
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

/**
 * [교육용 추가] 다온마켓 상품 후기 목록(관리자). 고객이 쓴 후기(mall-portal POST /daon/review)를 최신순으로 준다.
 * 관리자 화면 「상품 후기(교육용)」(mall-admin-web src/views/pms/review/index.vue)가 이 목록을 그린다. 수업 3-4·3-5(T07).
 * 권한: 상품 관리 권한(/product/**)과 같다(MallSecurityConfig).
 */
@RestController
@Tag(name = "DaonReviewController", description = "다온마켓 상품 후기 목록(교육용 추가)")
@RequestMapping("/daon/review")
public class DaonReviewController {
    @Autowired
    private PmsCommentMapper commentMapper;

    @Operation(summary = "상품 후기 목록(최신순)")
    @GetMapping("/list")
    public CommonResult<Map<String, Object>> list(@RequestParam(defaultValue = "20") Integer pageSize) {
        PageHelper.startPage(1, Math.max(1, Math.min(100, pageSize)));
        PmsCommentExample example = new PmsCommentExample();
        example.setOrderByClause("id desc");
        List<Map<String, Object>> rows = commentMapper.selectByExampleWithBLOBs(example).stream()
                .map(DaonReviewController::row)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", rows);
        // [랩 스위치] secure-kit.html-sanitize.enabled=false면 관리자 화면이 고치기 전처럼 본문을 HTML로 그린다(영상용, 3-5 재연)
        body.put("textOnly", SecureKitSwitch.on("html-sanitize"));
        return CommonResult.success(body);
    }

    private static Map<String, Object> row(PmsComment c) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        r.put("productId", c.getProductId());
        r.put("productName", c.getProductName());
        r.put("memberNickName", c.getMemberNickName());
        r.put("memberIp", c.getMemberIp());
        r.put("star", c.getStar());
        r.put("content", c.getContent());
        r.put("createTime", c.getCreateTime());
        return r;
    }
}

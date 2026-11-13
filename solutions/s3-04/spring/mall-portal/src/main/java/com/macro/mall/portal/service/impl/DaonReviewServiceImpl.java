package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.PmsCommentMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.DaonReviewParam;
import com.macro.mall.portal.service.DaonReviewService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [교육용 추가] 다온마켓 상품 후기 저장. 원본 표 pms_comment를 쓴다.
 */
@Service
public class DaonReviewServiceImpl implements DaonReviewService {
    // 다온마켓 3-4: 후기 길이 상한. 서식은 받지 않는다(글자 그대로 저장, 바꾸기는 출력하는 곳에서)
    static final int MAX_CONTENT = 500;
    /** 태그처럼 보이는 것: <script, </b, <img … (「1<2」처럼 꺾쇠 뒤가 글자가 아니면 태그가 아니다) */
    private static final Pattern TAG = Pattern.compile("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9-]{0,20})");
    @Autowired
    private PmsCommentMapper commentMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private HttpServletRequest request;

    @Override
    public Long create(DaonReviewParam param) {
        UmsMember current = memberService.getCurrentMember();
        UmsMember member = memberMapper.selectByPrimaryKey(current.getId());
        PmsProduct product = param.getProductId() == null ? null : productMapper.selectByPrimaryKey(param.getProductId());
        if (product == null || product.getPublishStatus() == null || product.getPublishStatus() != 1) {
            Asserts.fail("후기를 쓸 수 없는 상품입니다");
        }
        String content = param.getContent() == null ? "" : param.getContent();
        if (content.isBlank()) {
            Asserts.fail("후기 본문이 비었습니다");
        }
        int star = param.getStar() == null ? 5 : Math.max(1, Math.min(5, param.getStar()));
        // 다온마켓 3-4: 길이 상한. 본문은 원래 글자 그대로 저장한다(&lt;로 바꿔 저장하면 화면에 &lt;가 보이는 이중 인코딩)
        if (content.length() > MAX_CONTENT) {
            Asserts.fail("후기는 " + MAX_CONTENT + "자까지 쓸 수 있습니다");
        }
        PmsComment c = new PmsComment();
        c.setProductId(product.getId());
        c.setProductName(product.getName());
        c.setMemberNickName(member.getNickname() != null ? member.getNickname() : member.getUsername());
        c.setMemberIp(request.getRemoteAddr());
        c.setStar(star);
        c.setContent(content);
        c.setCreateTime(new Date());
        c.setShowStatus(1);
        commentMapper.insertSelective(c);
        // 다온마켓 3-4 ⑤ 탐지: 정상 고객은 후기에 태그를 쓰지 않는다. 태그 이름만 남기고 본문은 남기지 않는다
        Set<String> tags = tagNames(content);
        if (!tags.isEmpty()) {
            SecurityEventLogger.suspicious("input.sanitized.html", "3-4")
                    .actor("member:" + member.getId())
                    .target("review:" + c.getId())
                    .reason("허용 밖 태그 발견: " + String.join(",", tags))
                    .log();
        }
        return c.getId();
    }

    /** 본문에서 태그 이름만 모은다(소문자, 다섯 개까지). 후기에는 허용하는 태그가 없다 */
    static Set<String> tagNames(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find() && out.size() < 5) {
            out.add(m.group(1).toLowerCase());
        }
        return out;
    }
}

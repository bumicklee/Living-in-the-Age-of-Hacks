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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * [교육용 추가] 다온마켓 상품 후기 저장. 원본 표 pms_comment를 쓴다.
 */
@Service
public class DaonReviewServiceImpl implements DaonReviewService {
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
        // ✏️ 3-4: 받은 본문을 그대로 저장한다. 길이 상한이 없다(다온마켓 교육용 표시)
        PmsComment c = new PmsComment();
        c.setProductId(product.getId());
        c.setProductName(product.getName());
        c.setMemberNickName(member.getNickname() != null ? member.getNickname() : member.getUsername());
        c.setMemberIp(request.getRemoteAddr());   // ✏️ 6-4(E09): ClientIp.of(request)로(믿는 프록시가 넣은 값만)
        c.setStar(star);
        c.setContent(content);
        c.setCreateTime(new Date());
        c.setShowStatus(1);
        commentMapper.insertSelective(c);
        return c.getId();
    }
}

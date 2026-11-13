package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 주문 반품 관리 Service 구현 클래스
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private UmsMemberService memberService;
    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        if (!SecureKitSwitch.on("ownership")) {
            return createUnchecked(returnApply);   // [랩 스위치] secure-kit.ownership.enabled=false: 고치기 전 모습(영상용)
        }
        // 다온마켓 2-미션: 2-2와 같은 모양. 소유권은 조회 조건에 넣는다
        UmsMember member = memberService.getCurrentMember();
        List<OmsOrder> orders = List.of();
        if (returnApply.getOrderId() != null) {
            OmsOrderExample example = new OmsOrderExample();
            example.createCriteria().andIdEqualTo(returnApply.getOrderId())
                    .andMemberIdEqualTo(member.getId()).andDeleteStatusEqualTo(0);
            orders = orderMapper.selectByExample(example);
        }
        if (orders.isEmpty()) {
            // ⑤ 탐지: 남의 주문(또는 없는 주문)으로 반품을 신청했다
            SecurityEventLogger.denied("access.denied.not_owner", "2-mission")
                    .actor("member:" + member.getId())
                    .target("order:" + returnApply.getOrderId())
                    .reason("본인 주문이 아니거나 없는 주문으로 반품 신청")
                    .log();
            Asserts.fail("주문을 찾을 수 없습니다");
        }
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply,realApply);
        // 주문 번호·신청자 이름은 요청 값이 아니라 DB와 로그인 정보에서 채운다
        realApply.setOrderSn(orders.get(0).getOrderSn());
        realApply.setMemberUsername(member.getUsername());
        realApply.setCreateTime(new Date());
        realApply.setStatus(0);
        return returnApplyMapper.insert(realApply);
    }

    // 고치기 전 모습. 랩 스위치를 껐을 때만 쓴다
    private int createUnchecked(OmsOrderReturnApplyParam returnApply) {
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply,realApply);
        realApply.setCreateTime(new Date());
        realApply.setStatus(0);
        return returnApplyMapper.insert(realApply);
    }
}

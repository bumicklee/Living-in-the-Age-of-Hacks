package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.portal.dao.DaonPaymentDao;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.security.event.SecurityEventLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [다온마켓 10-4 정답] 결제사 웹훅 처리: 서명(컨트롤러) → ② 중복 → ③ 금액 → ④ 상태 전이 순서.
 *
 * 상태 전이표(이 상수와 수업 카드의 표 하나만 고친다). 허용하지 않는 화살표는 모두 거절한다.
 * <pre>
 *   0 결제 대기 → 1 결제 완료 → 2 발송 → 3 완료
 *   0 결제 대기 → 4 취소(닫힘)
 * </pre>
 * 결제 완료 전이는 조건부 UPDATE(status = 0일 때만). 재고 차감은 그 UPDATE가 1행일 때만 한다.
 */
@Service
public class DaonPaymentService {
    public static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 4),
            1, Set.of(2),
            2, Set.of(3));
    static final String APPROVED = "payment.approved";

    public enum Outcome { APPLIED, DUPLICATE, IGNORED, NO_ORDER, AMOUNT_MISMATCH, BAD_TRANSITION }

    @Autowired
    private DaonPaymentDao paymentDao;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private PortalOrderDao portalOrderDao;

    /** 서명이 맞은 웹훅 하나. 같은 트랜잭션 안에서 기록·전이·재고가 함께 성공하거나 함께 되돌아간다 */
    @Transactional
    public Outcome apply(String event, String paymentId, String orderSn, BigDecimal amount) {
        // ② 중복: 이벤트 기록을 먼저 넣는다. 같은 (payment_id, event)가 있으면 아무것도 하지 않는다
        try {
            paymentDao.insertEvent(paymentId, event, orderSn, amount);
        } catch (DuplicateKeyException e) {
            SecurityEventLogger.suspicious("payment.duplicate.event", "10-4")
                    .actor("system:pay-webhook")
                    .target("payment:" + paymentId)
                    .reason("같은 결제 이벤트가 다시 옴(처리하지 않고 200)")
                    .log();
            return Outcome.DUPLICATE;
        }
        if (!APPROVED.equals(event)) {
            paymentDao.markResult(paymentId, event, "ignored");
            return Outcome.IGNORED;
        }
        OmsOrder order = findBySn(orderSn);
        if (order == null) {
            paymentDao.markResult(paymentId, event, "no_order");
            return Outcome.NO_ORDER;
        }
        // ③ 금액: 결제사가 승인한 금액과 주문의 결제 금액이 같아야 한다. 값은 이벤트에 넣지 않는다
        if (order.getPayAmount() == null || amount == null || order.getPayAmount().compareTo(amount) != 0) {
            paymentDao.markResult(paymentId, event, "amount_mismatch");
            SecurityEventLogger.denied("payment.denied.amount_mismatch", "10-4")
                    .actor("system:pay-webhook")
                    .target("order:" + order.getId())
                    .reason("웹훅 승인 금액과 주문 결제 금액이 다름(상태 바꾸지 않음)")
                    .log();
            return Outcome.AMOUNT_MISMATCH;
        }
        // ④ 상태 전이: 0 → 1만. 0행이면 이미 결제 완료·취소된 주문(역행·중복 전이)
        if (paymentDao.markPaid(orderSn, 1) == 0) {
            paymentDao.markResult(paymentId, event, "bad_transition");
            SecurityEventLogger.denied("payment.denied.bad_transition", "10-4")
                    .actor("system:pay-webhook")
                    .target("order:" + order.getId())
                    .reason("status=0 조건 0행(지금 상태 " + order.getStatus() + "에서 결제 완료로 갈 수 없음)")
                    .log();
            return Outcome.BAD_TRANSITION;
        }
        OmsOrderDetail detail = portalOrderDao.getDetail(order.getId());
        if (detail != null && detail.getOrderItemList() != null && !detail.getOrderItemList().isEmpty()) {
            portalOrderDao.updateSkuStock(detail.getOrderItemList());       // 잠금 재고를 실제 재고 차감으로(한 번만)
        }
        paymentDao.markResult(paymentId, event, "applied");
        return Outcome.APPLIED;
    }

    public static boolean allowed(int from, int to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private OmsOrder findBySn(String orderSn) {
        OmsOrderExample ex = new OmsOrderExample();
        ex.createCriteria().andOrderSnEqualTo(orderSn).andDeleteStatusEqualTo(0);
        List<OmsOrder> list = orderMapper.selectByExample(ex);
        return list.isEmpty() ? null : list.get(0);
    }
}

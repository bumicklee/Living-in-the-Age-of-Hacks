package com.macro.mall.portal.dao;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * [다온마켓 10-4] 결제 웹훅 기록과 주문 상태 전이.
 *
 * - oms_payment_event(payment_id, event) 유니크: 같은 웹훅이 두 번 오면 두 번째 INSERT가 중복 키로 실패한다(멱등).
 * - 상태 전이는 조건부 UPDATE: 결제 대기(0)일 때만 결제 완료(1)로. 0행이면 역행·중복 전이.
 */
public interface DaonPaymentDao {
    int insertEvent(@Param("paymentId") String paymentId, @Param("event") String event,
                    @Param("orderSn") String orderSn, @Param("amount") BigDecimal amount);

    int markResult(@Param("paymentId") String paymentId, @Param("event") String event, @Param("result") String result);

    /** 0 결제 대기 → 1 결제 완료. 1이면 성공 */
    int markPaid(@Param("orderSn") String orderSn, @Param("payType") int payType);
}

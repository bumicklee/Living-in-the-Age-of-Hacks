package com.macro.mall.portal.dao;

import org.apache.ibatis.annotations.Param;

/**
 * [다온마켓 10-2] 돈·수량을 바꾸는 SQL은 「검사와 갱신을 한 문장」으로 둔다. 반환값(영향받은 행 수)이 0이면 실패다.
 *
 * 읽고 → if → 쓰기는 두 요청이 같은 값을 읽고 둘 다 통과한다(TOCTOU, 10-1). 조건을 WHERE에 넣으면 DB가 한 줄씩 차례로 처리한다.
 */
public interface DaonMoneyDao {
    /** 쿠폰 수량 하나 꺼내기: count &gt; 0일 때만. 1이면 성공 */
    int takeCoupon(@Param("couponId") Long couponId);

    /** 포인트 쓰기: 잔액이 use 이상일 때만. 1이면 성공 */
    int useIntegration(@Param("memberId") Long memberId, @Param("use") int use);

    /** 포인트 돌려주기(주문 취소): 캐시된 회원 객체 값이 아니라 DB 값에 더한다 */
    int refundIntegration(@Param("memberId") Long memberId, @Param("amount") int amount);

    /** 재고 잠그기: 남은 재고(stock - lock_stock)가 qty 이상일 때만. 1이면 성공 */
    int lockStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}

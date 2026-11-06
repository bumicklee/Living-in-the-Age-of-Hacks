package com.macro.mall.portal.service;


import com.macro.mall.portal.domain.AliPayParam;

import java.util.Map;

/**
 * @auther macrozheng
 * @description 알리페이 결제Service
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
public interface AlipayService {
    /**
     * 제출 파라미터로 PC 결제 페이지 생성
     */
    String pay(AliPayParam aliPayParam);

    /**
     * 알리페이 비동기 콜백 처리
     */
    String notify(Map<String, String> params);

    /**
     * 알리페이 거래 상태 조회
     * @param outTradeNo 가맹점 주문 번호
     * @param tradeNo 알리페이 거래 번호
     * @return 알리페이 거래 상태
     */
    String query(String outTradeNo, String tradeNo);

    /**
     * 제출 파라미터로 모바일 결제 페이지 생성
     */
    String webPay(AliPayParam aliPayParam);
}

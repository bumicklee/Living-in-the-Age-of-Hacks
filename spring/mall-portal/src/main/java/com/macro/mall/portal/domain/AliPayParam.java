package com.macro.mall.portal.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @auther macrozheng
 * @description 알리페이 결제 요청 파라미터
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Data
public class AliPayParam {
    /**
     * 가맹점 주문번호, 가맹점이 직접 정의하며 고유해야 함
     */
    private String outTradeNo;
    /**
     * 상품 제목/거래 제목/주문 제목/주문 키워드 등
     */
    private String subject;
    /**
     * 주문 총 금액, 단위는 원, 소수점 둘째 자리까지
     */
    private BigDecimal totalAmount;
}

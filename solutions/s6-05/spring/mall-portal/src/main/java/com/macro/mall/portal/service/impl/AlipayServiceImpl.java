package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.AlipayConfig;
import com.macro.mall.portal.domain.AliPayParam;
import com.macro.mall.portal.service.AlipayService;
import com.macro.mall.portal.service.OmsPortalOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @auther macrozheng
 * @description 알리페이 결제 Service 구현 클래스
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private AlipayConfig alipayConfig;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Override
    public String pay(AliPayParam aliPayParam) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //비동기 수신 주소, 외부(공인망)에서 접근 가능해야 함
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //동기 이동 주소
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******필수 파라미터******
        JSONObject bizContent = new JSONObject();
        //가맹점 주문번호, 가맹점이 직접 정의하며 고유해야 함
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //주문 총 금액은 데이터베이스 주문 테이블의 값을 기준으로 함
        OmsOrder order = portalOrderService.getOrderByOrderSn(aliPayParam.getOutTradeNo());
        if(order==null||order.getPayAmount()==null){
            Asserts.fail("주문 정보 또는 금액은 비어 있을 수 없습니다!");
        }
        BigDecimal totalAmount = order.getPayAmount();
        //결제 금액, 최솟값 0.01위안
        bizContent.put("total_amount", totalAmount);
        //주문 제목, 특수 문자 사용 불가
        bizContent.put("subject", aliPayParam.getSubject());
        //PC 웹사이트 결제 시나리오에서 고정 전달 값FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            org.slf4j.LoggerFactory.getLogger(AlipayServiceImpl.class).error("처리 중 오류", e);
        }
        return formHtml;
    }

    @Override
    public String notify(Map<String, String> params) {
        String result = "failure";
        boolean signVerified = false;
        try {
            //SDK를 호출하여 서명 검증
            signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), alipayConfig.getCharset(), alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            log.error("결제 콜백 서명 검증 중 예외 발생!",e);
            org.slf4j.LoggerFactory.getLogger(AlipayServiceImpl.class).error("처리 중 오류", e);
        }
        if (signVerified) {
            String tradeStatus = params.get("trade_status");
            if("TRADE_SUCCESS".equals(tradeStatus)){
                result = "success";
                log.info("notify메서드가 호출되었습니다,tradeStatus:{}",tradeStatus);
                String outTradeNo = params.get("out_trade_no");
                portalOrderService.paySuccessByOrderSn(outTradeNo,1);
            }else{
                log.warn("주문 결제가 성공하지 못했습니다,trade_status:{}",tradeStatus);
            }
        } else {
            log.warn("결제 콜백 서명 검증 실패!");
        }
        return result;
    }

    @Override
    public String query(String outTradeNo, String tradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        //******필수 파라미터******
        JSONObject bizContent = new JSONObject();
        //조회 파라미터 설정, out_trade_no와 trade_no 중 최소 하나는 전달
        if(StrUtil.isNotEmpty(outTradeNo)){
            bizContent.put("out_trade_no",outTradeNo);
        }
        if(StrUtil.isNotEmpty(tradeNo)){
            bizContent.put("trade_no",tradeNo);
        }
        //거래 정산 정보: trade_settle_info
        String[] queryOptions = {"trade_settle_info"};
        bizContent.put("query_options", queryOptions);
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            log.error("알리페이 청구서 조회 중 예외 발생!",e);
        }
        if(response.isSuccess()){
            log.info("알리페이 청구서 조회 성공!");
            if("TRADE_SUCCESS".equals(response.getTradeStatus())){
                portalOrderService.paySuccessByOrderSn(outTradeNo,1);
            }
        } else {
            log.error("알리페이 청구서 조회 실패!");
        }
        //거래 상태: WAIT_BUYER_PAY(거래 생성, 구매자 결제 대기), TRADE_CLOSED(미결제 거래 시간 초과로 종료되었거나 결제 완료 후 전액 환불), TRADE_SUCCESS(거래 결제 성공), TRADE_FINISHED(거래 종료, 환불 불가)
        return response.getTradeStatus();
    }

    @Override
    public String webPay(AliPayParam aliPayParam) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest ();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //비동기 수신 주소, 외부(공인망)에서 접근 가능해야 함
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //동기 이동 주소
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******필수 파라미터******
        JSONObject bizContent = new JSONObject();
        //가맹점 주문번호, 가맹점이 직접 정의하며 고유해야 함
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //주문 총 금액은 데이터베이스 주문 테이블의 값을 기준으로 함
        OmsOrder order = portalOrderService.getOrderByOrderSn(aliPayParam.getOutTradeNo());
        if(order==null||order.getPayAmount()==null){
            Asserts.fail("주문 정보 또는 금액은 비어 있을 수 없습니다!");
        }
        BigDecimal totalAmount = order.getPayAmount();
        //결제 금액, 최솟값 0.01위안
        bizContent.put("total_amount", totalAmount);
        //주문 제목, 특수 문자 사용 불가
        bizContent.put("subject", aliPayParam.getSubject());
        //모바일 웹 결제 기본 전달 값FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "QUICK_WAP_WAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            org.slf4j.LoggerFactory.getLogger(AlipayServiceImpl.class).error("처리 중 오류", e);
        }
        return formHtml;
    }
}

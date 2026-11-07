package com.macro.mall.portal.component;

import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 *  주문 취소 메시지 수신자
 * Created by macro on 2018/9/14.
 */
@Component
@RabbitListener(queues = "mall.order.cancel")
public class CancelOrderReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancelOrderReceiver.class);
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @RabbitHandler
    public void handle(Long orderId, @Header(name = TraceId.HEADER, required = false) String traceId){
        // [다온마켓 추가] 주문을 만든 요청의 추적 번호를 이어 받는다(TraceRabbitConfig가 보낼 때 붙인다)
        String id = TraceId.accept(traceId);
        MDC.put(TraceId.MDC_KEY, id != null ? id : TraceId.newId());
        try {
            portalOrderService.cancelOrder(orderId);
            LOGGER.info("process orderId:{}",orderId);
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }
}

package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.DaonPayQueueConfig;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * [교육용 추가 T24] 결제 요청 큐 소비자. 메시지 하나 = 결제 요청 하나.
 *
 * 9-4 시작 상태: 결제가 실패하면 같은 큐 맨 뒤에 바로 다시 넣는다(모두 3번). 결제 클라이언트도 안에서 3번 하므로
 * 결제사가 느려지면 주문 하나에 결제사 요청이 최대 9번 간다.
 */
@Component
public class DaonPayRequestReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(DaonPayRequestReceiver.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int DELIVERIES = 3;

    @Autowired
    private DaonPaymentClient client;
    @Autowired
    private RabbitTemplate rabbit;

    @RabbitListener(queues = DaonPayQueueConfig.QUEUE, concurrency = "3")
    public void handle(Message message) {
        Object h = message.getMessageProperties().getHeader(TraceId.HEADER);
        String id = TraceId.accept(h == null ? null : h.toString());
        MDC.put(TraceId.MDC_KEY, id != null ? id : TraceId.newId());
        try {
            int attempt = attempt(message);
            JsonNode req;
            try {
                req = JSON.readTree(message.getBody());
            } catch (java.io.IOException e) {
                LOG.warn("결제 요청 메시지 형식이 다름, 버림");     // 다시 넣으면 같은 실패가 끝없이 돈다
                return;
            }
            String orderSn = req.path("orderSn").asText();
            try {
                String paymentId = client.pay(orderSn, req.path("amount").decimalValue(), req.path("key").asText(), TraceId.current());
                LOG.info("결제 승인: 주문 {} → {}", orderSn, paymentId);
            } catch (RuntimeException e) {
                if (attempt + 1 < DELIVERIES) {
                    // 실패하면 바로 다시 넣는다(간격 없음)
                    rabbit.send("", DaonPayQueueConfig.QUEUE, MessageBuilder.fromMessage(message)
                            .setHeader(DaonPayQueueConfig.ATTEMPT_HEADER, attempt + 1).build());
                    LOG.warn("결제 실패, 다시 넣음({}번째): 주문 {} · {}", attempt + 1, orderSn, e.getMessage());
                } else {
                    LOG.warn("결제 실패, 포기: 주문 {} · {}", orderSn, e.getMessage());
                }
            }
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }

    static int attempt(Message message) {
        Object a = message.getMessageProperties().getHeader(DaonPayQueueConfig.ATTEMPT_HEADER);
        try {
            return a == null ? 0 : Integer.parseInt(a.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

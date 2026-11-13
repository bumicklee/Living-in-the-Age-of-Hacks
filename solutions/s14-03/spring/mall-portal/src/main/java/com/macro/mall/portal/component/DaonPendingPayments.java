package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.DaonPayQueueConfig;
import com.macro.mall.security.securekit.DegradedMode;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [다온마켓 14-3 정답] 「결제 대기」 큐. 축소 운영 동안 받은 결제 요청을 여기(daon.pay.pending)에 쌓아 두고, 축소 운영이 끝나면 천천히 옮긴다.
 *
 * - 쌓기: DaonCheckoutController가 축소 운영이면 hold(). 메시지 모양은 9-4·9-5 결제 요청 큐(daon.pay.request)와 같고,
 *   요청의 X-Trace-Id를 메시지 헤더로 그대로 싣는다(14-4 타임라인에서 같은 번호로 이어 찾는다). RabbitMQ 관리 화면에서 쌓인 수가 보인다.
 * - 옮기기: 1초마다 축소 운영이 아니면 최대 DRAIN_PER_SECOND(3)개만 결제 요청 큐로 옮긴다. 한꺼번에 비우면 막 살아난 결제사에
 *   요청이 몰린다(9-5 재시도 예산과 같은 생각). 옮긴 뒤 결제 요청 큐의 소비자(DaonPayRequestReceiver, 9-5)가 결제사를 부른다.
 * - 축소 운영 중에는 아무것도 옮기지 않는다(큐에 그대로 있다). 앱이 다시 떠도 큐는 남는다(durable).
 */
@Configuration
public class DaonPendingPayments {
    private static final Logger LOG = LoggerFactory.getLogger(DaonPendingPayments.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String QUEUE = "daon.pay.pending";
    static final int DRAIN_PER_SECOND = 3;

    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private DegradedMode degradedMode;

    @Bean
    public Queue daonPayPendingQueue() {
        return new Queue(QUEUE, true);
    }

    public void hold(Long orderId, String orderSn, BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("orderSn", orderSn);
        body.put("amount", amount);
        body.put("key", "pay-start-" + orderSn);                 // 10-4 결제 시작과 같은 멱등 키(결제사가 두 번 처리하지 않게)
        byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String trace = TraceId.current();
        rabbit.send("", QUEUE, MessageBuilder.withBody(bytes)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(DaonPayQueueConfig.ATTEMPT_HEADER, 0)
                .setHeader(TraceId.HEADER, trace == null ? TraceId.newId() : trace)
                .build());
        LOG.info("[14-3] 축소 운영: 결제 요청을 대기 큐에 넣음(주문 {})", orderSn);
    }

    @Scheduled(fixedDelay = 1000L, initialDelay = 10000L)
    public void drain() {
        if (degradedMode.isOn(DegradedMode.PAYMENT)) {
            return;
        }
        for (int i = 0; i < DRAIN_PER_SECOND; i++) {
            Message m;
            try {
                m = rabbit.receive(QUEUE);
            } catch (RuntimeException e) {
                return;                                             // 큐에 닿지 않으면 다음 번에
            }
            if (m == null) {
                return;
            }
            rabbit.send("", DaonPayQueueConfig.QUEUE, m);
            LOG.info("[14-3] 축소 운영이 끝나 대기 결제를 결제 요청 큐로 옮김");
        }
    }
}

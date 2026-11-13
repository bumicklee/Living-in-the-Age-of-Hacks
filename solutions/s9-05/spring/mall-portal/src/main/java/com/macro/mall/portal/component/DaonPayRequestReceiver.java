package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.DaonPayQueueConfig;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [교육용 추가 T24 · 9-5 정답] 결제 요청 큐 소비자. 재시도는 여기 한 곳에서만 한다.
 *
 * 실패하면(시간 초과·5xx·서킷 열림)
 *   ① 재시도 3번을 넘었으면 → DLQ
 *   ② 재시도 예산(RetryBudget)이 바닥났으면 → resilience.retry_budget.exhausted + DLQ
 *   ③ 아니면 → 재시도 큐(daon.pay.retry)에 지연(지수 백오프 1·2·4초 + 지터)을 달아 보낸다. 만료되면 RabbitMQ가 원래 큐로 돌려준다(A6)
 * 결제사의 거절(4xx)은 다시 보내지 않고 바로 DLQ.
 * DLQ로 보낼 때는 queue.dead_lettered를 남기고 메시지를 거절한다(requeue 없음) → 큐 정책(DLX)이 daon.pay.dlq로 옮긴다.
 * 메시지 헤더의 X-Trace-Id는 재시도·DLQ까지 그대로 따라간다(주문 요청 로그와 이어 찾기).
 */
@Component
public class DaonPayRequestReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(DaonPayRequestReceiver.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int DELIVERIES = 3;                  // 옛 동작(스위치를 껐을 때)
    static final int MAX_RETRIES = 3;
    static final long BASE_DELAY_MS = 1000;

    @Autowired
    private DaonPaymentClient client;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RetryBudget budget;

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
                deadLetter("메시지 형식이 다름", "?");
                return;
            }
            String orderSn = req.path("orderSn").asText();
            boolean resilient = SecureKitSwitch.on("resilience");
            if (resilient && attempt == 0) {
                budget.recordRequest();
            }
            try {
                String paymentId = client.pay(orderSn, req.path("amount").decimalValue(), req.path("key").asText(), TraceId.current());
                LOG.info("결제 승인: 주문 {} → {} ({}번째 시도)", orderSn, paymentId, attempt + 1);
            } catch (DaonPaymentClient.PaymentFailedException e) {
                if (e.isRejected()) {
                    deadLetter("결제사가 거절(4xx), 다시 보내지 않음", orderSn);
                }
                retryOrGiveUp(message, attempt, orderSn, e.getMessage(), resilient);
            } catch (RuntimeException e) {          // CallNotPermittedException(서킷 열림) 등
                retryOrGiveUp(message, attempt, orderSn, e.getClass().getSimpleName(), resilient);
            }
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }

    private void retryOrGiveUp(Message message, int attempt, String orderSn, String why, boolean resilient) {
        if (!resilient) {                           // 옛 동작: 같은 큐 맨 뒤에 바로 다시(모두 3번)
            if (attempt + 1 < DELIVERIES) {
                rabbit.send("", DaonPayQueueConfig.QUEUE, MessageBuilder.fromMessage(message)
                        .setHeader(DaonPayQueueConfig.ATTEMPT_HEADER, attempt + 1).build());
            }
            return;
        }
        if (attempt >= MAX_RETRIES) {
            deadLetter("재시도 " + MAX_RETRIES + "번 넘음(" + why + ")", orderSn);
        }
        if (!budget.tryAcquireRetry()) {
            SecurityEventLogger.denied("resilience.retry_budget.exhausted", "9-5")
                    .actor("system:pay-worker")
                    .target("dependency:pay-stub")
                    .reason("재시도 예산 소진(최근 1분 " + budget.used() + "/" + budget.allowed() + "), 다시 보내지 않음")
                    .log();
            deadLetter("재시도 예산 소진(" + why + ")", orderSn);
        }
        long delay = backoff(attempt);
        rabbit.send("", DaonPayQueueConfig.RETRY_QUEUE, MessageBuilder.fromMessage(message)
                .setHeader(DaonPayQueueConfig.ATTEMPT_HEADER, attempt + 1)
                .setExpiration(String.valueOf(delay))
                .build());
        LOG.warn("결제 실패, {}ms 뒤 다시({}번째 재시도): 주문 {} · {}", delay, attempt + 1, orderSn, why);
    }

    /** 지수 백오프(1·2·4초) + 지터(절반은 무작위): 같은 순간에 몰리지 않게 흩는다 */
    static long backoff(int attempt) {
        long cap = BASE_DELAY_MS << Math.min(attempt, 5);
        return cap / 2 + ThreadLocalRandom.current().nextLong(cap / 2 + 1);
    }

    private void deadLetter(String reason, String orderSn) {
        SecurityEventLogger.denied("queue.dead_lettered", "9-5")
                .actor("system:pay-worker")
                .target("queue:" + DaonPayQueueConfig.QUEUE)
                .reason(reason + " · 주문 " + orderSn)
                .log();
        throw new AmqpRejectAndDontRequeueException(reason);     // 큐 정책(DLX)이 daon.pay.dlq로 옮긴다
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

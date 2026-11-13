package com.macro.mall.portal.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [교육용 추가 T24] 결제 요청 큐. 주문 뒤 결제 요청을 이 큐에 넣고, DaonPayRequestReceiver가 결제사(가짜 payment-stub)를 부른다.
 * 기본 익스체인지("")에 큐 이름을 라우팅 키로 보낸다. 재시도 큐·DLQ는 9-5(A6)에서 RabbitMQ 쪽에 만든다.
 */
@Configuration
public class DaonPayQueueConfig {
    public static final String QUEUE = "daon.pay.request";
    /** 9-5(A6): 재시도 큐(메시지마다 expiration, 만료되면 QUEUE로 돌아온다)·DLX·DLQ. infra/rabbitmq/a06-definitions.json */
    public static final String RETRY_QUEUE = "daon.pay.retry";
    public static final String DLQ = "daon.pay.dlq";
    public static final String ATTEMPT_HEADER = "x-daon-attempt";

    @Bean
    public Queue daonPayRequestQueue() {
        return new Queue(QUEUE, true);
    }
}

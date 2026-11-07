package com.macro.mall.portal.config;

import com.macro.mall.security.trace.TraceId;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [다온마켓 추가] 큐 메시지에도 추적 번호를 붙인다(랩-설계.md 11절).
 * 이 앱이 RabbitMQ로 보내는 모든 메시지에 지금 요청의 X-Trace-Id를 헤더로 넣는다(CancelOrderSender 등).
 * 받는 쪽(CancelOrderReceiver)은 이 헤더를 MDC에 다시 넣어, 30분 뒤 주문 취소 로그도 같은 번호로 찾을 수 있다.
 */
@Configuration
public class TraceRabbitConfig {
    @Bean
    public RabbitTemplateCustomizer traceIdHeader() {
        return template -> template.addBeforePublishPostProcessors(message -> {
            String id = TraceId.current();
            if (id != null) {
                message.getMessageProperties().setHeader(TraceId.HEADER, id);
            }
            return message;
        });
    }
}

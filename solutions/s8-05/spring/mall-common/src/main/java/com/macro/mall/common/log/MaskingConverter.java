package com.macro.mall.common.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * [다온마켓 8-5] 로그 메시지(%m)를 찍기 직전에 LogMask로 가리는 Logback 변환기(마지막 그물).
 * logback-spring.xml이 %m·%msg·%message를 이 변환기로 바꿔 끼운다. 원천에서 줄이는 것(WebLogAspect)이 먼저다.
 */
public class MaskingConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return LogMask.mask(super.convert(event));
    }
}

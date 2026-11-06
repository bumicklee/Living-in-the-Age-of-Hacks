package com.macro.mall.portal.domain;

import lombok.Getter;

/**
 * 메시지 큐 열거형 클래스
 * Created by macro on 2018/9/14.
 */
@Getter
public enum QueueEnum {
    /**
     * 메시지 알림 큐
     */
    QUEUE_ORDER_CANCEL("mall.order.direct", "mall.order.cancel", "mall.order.cancel"),
    /**
     * 메시지 알림 ttl 큐
     */
    QUEUE_TTL_ORDER_CANCEL("mall.order.direct.ttl", "mall.order.cancel.ttl", "mall.order.cancel.ttl");

    /**
     * 익스체인지 이름
     */
    private final String exchange;
    /**
     * 큐 이름
     */
    private final String name;
    /**
     * 라우팅 키
     */
    private final String routeKey;

    QueueEnum(String exchange, String name, String routeKey) {
        this.exchange = exchange;
        this.name = name;
        this.routeKey = routeKey;
    }
}

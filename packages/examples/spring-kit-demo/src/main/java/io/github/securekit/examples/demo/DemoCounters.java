package io.github.securekit.examples.demo;

import io.github.securekit.core.KitResettable;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** 시험이 「실제로 몇 번 처리했는지」를 보는 숫자(환불·웹훅·서킷 시험용 의존성 호출) */
@Component
public class DemoCounters implements KitResettable {
    public final AtomicInteger refunds = new AtomicInteger();
    public final AtomicInteger webhooks = new AtomicInteger();
    public final AtomicInteger flakyHits = new AtomicInteger();

    @Override
    public void reset() {
        refunds.set(0);
        webhooks.set(0);
        flakyHits.set(0);
    }
}

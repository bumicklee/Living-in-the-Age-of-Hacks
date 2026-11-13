package io.github.securekit.examples.demo;

import io.github.securekit.core.SecureKitException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 예제 전용 랩 주소(/_kit/*). 부품 쪽 랩 주소는 스타터가 연다. 둘 다 secure-kit.lab.expose: true 일 때만 */
@RestController
@RequestMapping("/_kit")
@ConditionalOnProperty(prefix = "secure-kit.lab", name = "expose", havingValue = "true")
public class DemoLabController {
    private final MailOutbox outbox;
    private final PiiStore pii;
    private final DemoCounters counters;

    public DemoLabController(MailOutbox outbox, PiiStore pii, DemoCounters counters) {
        this.outbox = outbox;
        this.pii = pii;
        this.counters = counters;
    }

    @GetMapping("/outbox")
    public Map<String, Object> outbox() {
        return Map.of("mails", outbox.recent());
    }

    @GetMapping("/pii/raw/{id}")
    public Map<String, Object> raw(@PathVariable("id") long id) {
        String raw = pii.raw(id);
        if (raw == null) throw new SecureKitException(404, "찾을 수 없습니다");
        return Map.of("raw", raw);
    }

    /** 늘 503인 의존성(서킷 시험용) */
    @GetMapping("/flaky")
    public ResponseEntity<Map<String, Object>> flaky() {
        counters.flakyHits.incrementAndGet();
        return ResponseEntity.status(503).body(Map.of("ok", false));
    }

    @GetMapping("/flaky/count")
    public Map<String, Object> flakyCount() {
        return Map.of("count", counters.flakyHits.get());
    }

    @PostMapping("/flaky/reset")
    public ResponseEntity<Void> flakyReset() {
        counters.flakyHits.set(0);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/refund/count")
    public Map<String, Object> refunds() {
        return Map.of("count", counters.refunds.get());
    }

    @GetMapping("/webhook/count")
    public Map<String, Object> webhooks() {
        return Map.of("count", counters.webhooks.get());
    }
}

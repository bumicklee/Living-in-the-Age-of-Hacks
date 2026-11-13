package io.github.securekit.examples.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.web.KitPrincipal;
import io.github.securekit.web.annotation.Budget;
import io.github.securekit.web.annotation.FailClosed;
import io.github.securekit.web.annotation.Idempotent;
import io.github.securekit.web.annotation.PartnerScope;
import io.github.securekit.web.annotation.RequiresApproval;
import io.github.securekit.web.part.ResilientClient;
import io.github.securekit.web.part.ResilientClients;
import io.github.securekit.web.part.WebhookVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 돈·외부 의존성 쪽 부품: fail-closed(18) · resilient-client(19) · idempotency(20) · budget(21)
 * · partner-scope(23) · approval(24)
 */
@RestController
public class PaymentDemoController {
    private final DemoCounters counters;
    private final WebhookVerifier webhooks;
    private final ResilientClient flaky;
    private final String flakyUrl;
    private final ObjectMapper json;

    public PaymentDemoController(DemoCounters counters, WebhookVerifier webhooks, ResilientClients clients,
                                 DemoProperties props, ObjectMapper json) {
        this.counters = counters;
        this.webhooks = webhooks;
        this.flaky = clients.create("flaky");
        this.flakyUrl = props.getFlakyUrl();
        this.json = json;
    }

    @PostMapping("/kit/refund")
    @Idempotent(header = "Idempotency-Key", ttl = "24h")
    public Map<String, Object> refund(@RequestBody Map<String, Object> body) {
        counters.refunds.incrementAndGet();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refundId", "rf-" + UUID.randomUUID().toString().substring(0, 8));
        out.put("orderId", body.get("orderId"));
        out.put("amount", body.get("amount"));
        out.put("status", "DONE");
        return out;
    }

    /** 서명은 받은 바이트 그대로 확인한다(JSON으로 다시 만들면 서명이 달라진다) */
    @PostMapping("/kit/webhook")
    public Map<String, Object> webhook(@RequestBody byte[] raw,
                                       @RequestHeader(name = "X-Timestamp", required = false) String ts,
                                       @RequestHeader(name = "X-Signature", required = false) String sig) throws IOException {
        webhooks.verify(ts, sig, raw);
        JsonNode event = json.readTree(raw);
        String eventId = event.path("eventId").asText("");
        if (eventId.isEmpty()) throw new SecureKitException(400, "eventId가 필요합니다");
        if (!webhooks.firstDelivery(eventId)) return Map.of("duplicate", true);
        counters.webhooks.incrementAndGet();
        return Map.of("processed", true);
    }

    @PostMapping("/kit/sms")
    @Budget(value = "sms", key = "phone")
    public ResponseEntity<Map<String, Object>> sms(@RequestBody Map<String, Object> body) {
        // 예제라 실제로 보내지 않는다
        return ResponseEntity.accepted().body(Map.of("sent", true));
    }

    @PostMapping("/kit/refund/high")
    @RequiresApproval(policy = "refund-high-value", amount = "amount")
    public Map<String, Object> refundHigh(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        KitPrincipal.requireUser(request).requireRole("admin");
        counters.refunds.incrementAndGet();
        return Map.of("status", "DONE");
    }

    /** 본인 확인 의존성이 예외 → 통과시키지 않고 503 */
    @GetMapping("/kit/failclosed")
    @FailClosed
    public Map<String, Object> failClosed(@RequestParam(name = "dep", defaultValue = "up") String dep) {
        if ("down".equals(dep)) throw new IllegalStateException("본인 확인 서버에 연결하지 못했다");
        return Map.of("ok", true);
    }

    @GetMapping("/kit/resilient")
    public Map<String, Object> resilient() {
        flaky.get(flakyUrl);
        return Map.of("ok", true);
    }

    @GetMapping("/kit/partner/orders")
    @PartnerScope("orders:read")
    public Map<String, Object> partnerOrders() {
        return Map.of("orders", List.of(Map.of("id", 7, "status", "SHIPPING"), Map.of("id", 8, "status", "READY")));
    }

    @GetMapping("/kit/partner/export")
    @PartnerScope("orders:export")
    public Map<String, Object> partnerExport() {
        return Map.of("exported", 0);
    }
}

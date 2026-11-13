package com.macro.mall.portal.partner;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.PartnerPrincipal;
import com.macro.mall.security.securekit.PartnerScope;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [교육용 추가 T33 · 다온마켓 12-2 정답] 파트너 API. 파트너 입구(A9, 게이트웨이 8087)로만, 파트너 토큰으로만 받는다.
 *
 * 바뀐 것(시작 지점 대비)
 *   인증   : 고정 키(X-Partner-Key) → 15분짜리 파트너 토큰(POST /partner/token, 클라이언트 자격 증명). 고정 키는 더 읽지 않는다
 *   범위   : * → 기능 단위. 목록·상세는 orders:read:assigned, 내보내기는 orders:export(기본 발급에서 빠짐)  ← @PartnerScope
 *   소유   : 조회 조건에 파트너(daon_partner_order.app_id)를 넣는다. 범위가 맞아도 남의 주문은 없는 주문(404)
 *   위치   : 파트너 입구에서 온 요청만(PartnerEntrance, 그 밖은 404)
 * 범위는 「무슨 기능」, 조회 조건은 「누구의 데이터」. 범위만 확인하면 범위 안에서 다시 모든 주문이 보인다.
 */
@RestController
@Tag(name = "DaonPartnerController", description = "[교육용 추가 12-2] 파트너 API")
@RequestMapping("/partner")
public class DaonPartnerController {
    static final int MAX_SIZE = 100;
    static final int EXPORT_MAX = 1000;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OmsOrderMapper orderMapper;

    @Operation(summary = "파트너 주문 목록(배정된 주문만, 쪽 나눔)")
    @PartnerScope("orders:read:assigned")
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orders(HttpServletRequest request,
                                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                                      @RequestParam(value = "size", defaultValue = "50") int size) {
        PartnerPrincipal partner = PartnerPrincipal.of(request);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), MAX_SIZE);
        Long total;
        List<Long> ids;
        if (ownOnly()) {
            total = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order o JOIN daon_partner_order po ON po.order_id = o.id"
                    + " WHERE o.delete_status = 0 AND po.app_id = ?", Long.class, partner.appId());
            ids = jdbc.queryForList("SELECT o.id FROM oms_order o JOIN daon_partner_order po ON po.order_id = o.id"
                    + " WHERE o.delete_status = 0 AND po.app_id = ? ORDER BY o.id LIMIT ? OFFSET ?", Long.class, partner.appId(), s, (p - 1) * s);
        } else {
            total = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0", Long.class);
            ids = jdbc.queryForList("SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY id LIMIT ? OFFSET ?", Long.class, s, (p - 1) * s);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", p);
        body.put("size", s);
        body.put("total", total);
        body.put("items", toItems(orderMapper, ids));
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "파트너 주문 상세(배정된 주문만)")
    @PartnerScope("orders:read:assigned")
    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> order(HttpServletRequest request, @PathVariable("id") Long id) {
        PartnerPrincipal partner = PartnerPrincipal.of(request);
        if (ownOnly()) {
            Integer mine = jdbc.queryForObject("SELECT COUNT(*) FROM daon_partner_order WHERE order_id = ? AND app_id = ?",
                    Integer.class, id, partner.appId());
            if (mine == null || mine == 0) {
                // 2-2와 같은 이벤트(소유는 조회 조건이 답한다). actor만 partner:
                SecurityEventLogger.denied("access.denied.not_owner", "12-2").request(request)
                        .actor(partner.actor()).target("order:" + id).reason("이 파트너에게 배정되지 않았거나 없는 주문").log();
                return error(HttpStatus.NOT_FOUND, "not_found");
            }
        }
        List<Map<String, Object>> found = toItems(orderMapper, List.of(id));
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "not_found");
        }
        return ResponseEntity.ok(found.get(0));
    }

    @Operation(summary = "파트너 주문 내보내기(배정된 주문 한 번에, 범위 orders:export)")
    @PartnerScope("orders:export")
    @GetMapping("/orders/export")
    public ResponseEntity<Map<String, Object>> export(HttpServletRequest request) {
        PartnerPrincipal partner = PartnerPrincipal.of(request);
        List<Long> ids = ownOnly()
                ? jdbc.queryForList("SELECT o.id FROM oms_order o JOIN daon_partner_order po ON po.order_id = o.id"
                + " WHERE o.delete_status = 0 AND po.app_id = ? ORDER BY o.id LIMIT ?", Long.class, partner.appId(), EXPORT_MAX)
                : jdbc.queryForList("SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY id LIMIT ?", Long.class, EXPORT_MAX);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", ids.size());
        body.put("items", toItems(orderMapper, ids));
        return ResponseEntity.ok(body);
    }

    /** [랩 스위치] partner-scope를 끄면 배정 조건도 뺀다(시작 지점처럼 모든 주문) */
    private static boolean ownOnly() {
        return SecureKitSwitch.on("partner-scope");
    }

    /** 주문 행 → 파트너 응답 칸. 받는 사람 전화는 MBG 매퍼(5-3 복호화)를 거쳐 읽는다 */
    static List<Map<String, Object>> toItems(OmsOrderMapper mapper, List<Long> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        OmsOrderExample ex = new OmsOrderExample();
        ex.createCriteria().andIdIn(ids);
        ex.setOrderByClause("id asc");
        for (OmsOrder o : mapper.selectByExample(ex)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderSn", o.getOrderSn());
            m.put("status", o.getStatus());
            m.put("deliveryCompany", o.getDeliveryCompany());
            m.put("deliverySn", o.getDeliverySn());
            m.put("receiverName", o.getReceiverName());
            m.put("receiverPhone", o.getReceiverPhone());
            m.put("receiverAddress", String.join(" ", nz(o.getReceiverProvince()), nz(o.getReceiverCity()),
                    nz(o.getReceiverRegion()), nz(o.getReceiverDetailAddress())).trim());
            out.add(m);
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        return ResponseEntity.status(status).body(body);
    }
}

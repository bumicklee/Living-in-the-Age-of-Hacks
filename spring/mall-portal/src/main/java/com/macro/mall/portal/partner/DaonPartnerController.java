package com.macro.mall.portal.partner;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [교육용 추가 T33] 파트너 API(12-2 시작 지점). 배송 파트너 앱(stubs/partner)이 주문을 받아 간다.
 *
 * 지금 설정(12-1 확인 장면)
 *   인증   : 고정 키 하나(X-Partner-Key, daon_partner_app.api_key). 키가 맞으면 어느 파트너든 통과
 *   범위   : *  (주문 목록·상세·내보내기 전부)
 *   만료   : 없음(키를 바꾸려면 파트너와 날짜를 맞춰 한 번에 바꿔야 한다)
 *   위치   : 어디서 오든 받는다(고객 입구 8088·앱 주소 8085로도 열림)
 * 조회 조건에 파트너가 없다: 배정된 주문만 필요한 파트너가 300건 전부와 받는 사람 정보를 받는다(12-2에서 고친다).
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

    @Operation(summary = "파트너 주문 목록(쪽 나눔)")
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orders(@RequestHeader(value = "X-Partner-Key", required = false) String key,
                                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                                      @RequestParam(value = "size", defaultValue = "50") int size) {
        if (appOf(key) == null) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_key");
        }
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), MAX_SIZE);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0", Long.class);
        List<Long> ids = jdbc.queryForList("SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY id LIMIT ? OFFSET ?",
                Long.class, s, (p - 1) * s);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", p);
        body.put("size", s);
        body.put("total", total);
        body.put("items", items(ids));
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "파트너 주문 상세")
    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> order(@RequestHeader(value = "X-Partner-Key", required = false) String key,
                                                     @PathVariable("id") Long id) {
        if (appOf(key) == null) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_key");
        }
        List<Map<String, Object>> found = items(List.of(id));
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "not_found");
        }
        return ResponseEntity.ok(found.get(0));
    }

    @Operation(summary = "파트너 주문 내보내기(한 번에)")
    @GetMapping("/orders/export")
    public ResponseEntity<Map<String, Object>> export(@RequestHeader(value = "X-Partner-Key", required = false) String key) {
        if (appOf(key) == null) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_key");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY id LIMIT ?", Long.class, EXPORT_MAX);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", ids.size());
        body.put("items", items(ids));
        return ResponseEntity.ok(body);
    }

    /** 고정 키 → 앱 ID. 키가 맞으면 어느 앱이든(범위 *) */
    private String appOf(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        List<String> ids = jdbc.queryForList("SELECT id FROM daon_partner_app WHERE api_key = ?", String.class, key);
        return ids.isEmpty() ? null : ids.get(0);
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

    private List<Map<String, Object>> items(List<Long> ids) {
        return toItems(orderMapper, ids);
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

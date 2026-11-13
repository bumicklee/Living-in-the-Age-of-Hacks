package com.macro.mall.daon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [다온마켓 13-4 정답] 지연 실행이 끝난 고액 환불을 가져가 실행한다.
 *
 * 승인 서비스는 실행하지 않는다(결정 #17). 두 번째 승인 → 지연 실행 창(랩 10초) → 서킷 브레이커를 지나 READY가 된 환불만
 * 3초마다 가져와, 13-3과 같은 실행기(서명·내용·만료·1회용 번호 확인)로 실행한다. 창 안에서 취소된 것은 READY가 되지 않는다.
 */
@Component
public class DaonApprovedRefundPoller {
    private static final Logger LOG = LoggerFactory.getLogger(DaonApprovedRefundPoller.class);

    @Autowired
    private DaonApprovalClient client;
    @Autowired
    private DaonRefundExecutor executor;
    /** 실패한 승인은 세 번까지만 다시 해 본다(같은 거부 줄이 3초마다 쌓이지 않게) */
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 3000L, initialDelay = 15000L)
    public void poll() {
        if (!client.configured()) {
            return;
        }
        for (Map<String, Object> r : client.ready()) {
            String id = String.valueOf(r.get("id"));
            Long returnId = Long.valueOf(String.valueOf(r.get("returnId")));
            if (failures.getOrDefault(id, 0) >= 3) {
                continue;
            }
            if (executor.execute(returnId, client.token(id))) {
                client.executed(id);
                failures.remove(id);
            } else {
                failures.merge(id, 1, Integer::sum);
                LOG.warn("[13-4] 지연 실행 환불을 실행하지 못했습니다: return:{} ({}번째, 이유는 SECURITY 줄)", returnId, failures.get(id));
            }
        }
    }
}

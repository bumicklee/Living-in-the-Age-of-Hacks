package com.macro.mall.security.secret;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.openbao.OpenBaoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [다온마켓 6-3] 동적 DB 자격증명의 임대(lease)를 만료 전에 늘린다. 늘리지 못하면 secret.lease.renew_failed를 남기고,
 * 계정이 끝나기 전에 앱이 스스로 내려간다(조용히 죽은 연결로 남지 않게. 다시 띄우면 새 계정을 받는다).
 */
final class LeaseKeeper {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-LAB");
    private static volatile boolean started;

    private LeaseKeeper() {
    }

    static synchronized void track(OpenBaoClient bao, String leaseId, int leaseSeconds, String role) {
        if (started || leaseId.isEmpty()) {
            return;
        }
        started = true;
        long intervalMs = Math.max(5_000L, Long.getLong("daon.lease.renew.interval.ms", leaseSeconds * 1000L / 3));
        Thread t = new Thread(() -> loop(bao, leaseId, leaseSeconds, role, intervalMs), "daon-lease-keeper");
        t.setDaemon(true);
        t.start();
    }

    private static void loop(OpenBaoClient bao, String leaseId, int leaseSeconds, String role, long intervalMs) {
        long expiresAt = System.currentTimeMillis() + leaseSeconds * 1000L;
        while (true) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
            try {
                long granted = bao.renewLease(leaseId, leaseSeconds);
                if (granted <= 0) {
                    throw new IllegalStateException("갱신 응답에 시간이 없음");
                }
                expiresAt = System.currentTimeMillis() + granted * 1000L;
            } catch (RuntimeException e) {
                SecurityEventLogger.denied("secret.lease.renew_failed", "6-3")
                        .actor("app:" + role)
                        .target("lease:database/creds/" + role)
                        .reason("동적 DB 자격증명 갱신 실패(" + e.getClass().getSimpleName() + ")")
                        .log();
                if (expiresAt - System.currentTimeMillis() < 2 * intervalMs) {
                    LOG.error("[6-3] 동적 DB 자격증명이 곧 끝나는데 갱신하지 못해 앱을 내립니다(다시 띄우면 새 계정을 받습니다)");
                    System.exit(1);
                }
            }
        }
    }
}

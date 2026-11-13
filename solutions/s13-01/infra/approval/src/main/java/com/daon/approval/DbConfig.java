package com.daon.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.Map;

/**
 * [A10] 승인 서비스 전용 DB 계정. 금고의 동적 자격증명(database/creds/approval, 6-3과 같은 방식)으로만 받는다.
 *
 * 이 계정이 할 수 있는 것(infra/approval/approval-init.sh의 역할 approval):
 *   읽기만: oms_order · oms_order_return_apply · ums_admin · ums_admin_role_relation · ums_role
 *   읽기·쓰기: daon_approval_request · daon_approval_breaker (이 서비스가 처음 뜰 때 만든다)
 * 관리자 앱의 표를 고칠 수 없다. 환불을 직접 실행하지도 않는다(결정 #17: 승인 서비스는 서명만 한다).
 *
 * 임대는 20분마다 갱신하고, 갱신이 안 되면(최대 수명 24시간) 새 계정을 받아 연결 풀에 바꿔 끼운다.
 */
@Configuration
public class DbConfig {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-APPROVAL");
    private final Bao bao;
    private volatile String leaseId;
    private HikariDataSource ds;

    public DbConfig(Bao bao) {
        this.bao = bao;
    }

    @Bean
    public DataSource dataSource() throws InterruptedException {
        JsonNode c = fetch();
        HikariConfig h = new HikariConfig();
        h.setJdbcUrl(Bao.env("DAON_APPROVAL_DB_URL",
                "jdbc:mysql://db:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true"));
        h.setUsername(c.path("data").path("username").asText());
        h.setPassword(c.path("data").path("password").asText());
        h.setMaximumPoolSize(4);
        h.setPoolName("approval");
        ds = new HikariDataSource(h);
        return ds;
    }

    /** 금고·DB 계정이 준비될 때까지 최대 60초 기다린다(approval-init 직후에 뜬다) */
    private JsonNode fetch() throws InterruptedException {
        for (int i = 0; ; i++) {
            try {
                JsonNode c = bao.readRaw("database/creds/approval");
                leaseId = c.path("lease_id").asText();
                LOG.info("[A10] 전용 DB 계정을 받았습니다(임대 {}초)", c.path("lease_duration").asLong());
                return c;
            } catch (Bao.BaoException e) {
                if (i >= 30) {
                    throw e;
                }
                Thread.sleep(2000);
            }
        }
    }

    @Scheduled(fixedDelay = 20 * 60 * 1000L, initialDelay = 20 * 60 * 1000L)
    public void keepLease() {
        try {
            long left = bao.writeRaw("sys/leases/renew", Map.of("lease_id", leaseId, "increment", 3600)).path("lease_duration").asLong(0);
            if (left > 30 * 60) {
                return;
            }
        } catch (RuntimeException e) {
            LOG.warn("[A10] DB 임대 갱신 실패: {}", e.getMessage());
        }
        try {
            JsonNode c = fetch();
            ds.getHikariConfigMXBean().setUsername(c.path("data").path("username").asText());
            ds.getHikariConfigMXBean().setPassword(c.path("data").path("password").asText());
            ds.getHikariPoolMXBean().softEvictConnections();
            LOG.info("[A10] 새 DB 계정으로 바꿨습니다");
        } catch (Exception e) {
            LOG.warn("[A10] 새 DB 계정을 받지 못했습니다: {}", e.getMessage());
        }
    }
}

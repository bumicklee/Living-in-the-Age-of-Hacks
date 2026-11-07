package com.macro.mall.daon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [교육용 추가] 다온 운영 도구(ops): 상태와 설정 값 확인. 로그인 없이 열린다(secure.ignored.urls의 /ops/**).
 * 운영자가 장애 때 "지금 무슨 설정으로 떠 있나"를 보려고 만든 도구다. 수업 2-5·2-6에서 다룬다.
 */
@RestController
@RequestMapping("/ops")
public class DaonOpsController {
    private static final String[] KEYS = {
            "spring.datasource.url", "spring.datasource.username", "spring.data.redis.host",
            "minio.endpoint", "minio.accessKey", "jwt.secret", "jwt.expiration"};
    @Autowired
    private Environment env;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("service", "daon-ops");
        r.put("status", "UP");
        return r;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("service", "daon-ops");
        for (String k : KEYS) {
            r.put(k, env.getProperty(k));
        }
        return r;
    }
}

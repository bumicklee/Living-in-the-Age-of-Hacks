package com.daon.approval;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * [A10] 정책 파일(policy.yml) 읽기. 파일은 이미지에 굽는다(/app/policy.yml). 못 읽으면 서비스가 뜨지 않는다(정책 없이 승인하지 않는다).
 * 값 이름은 "refund.high_value"처럼 점으로 찾는다.
 */
@Component
public class PolicyConfig {
    private final Map<String, Object> root;

    @SuppressWarnings("unchecked")
    public PolicyConfig() throws IOException {
        Path p = Path.of(Bao.env("DAON_APPROVAL_POLICY", "/app/policy.yml"));
        try (InputStream in = Files.newInputStream(p)) {
            root = (Map<String, Object>) new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private Object get(String dotted) {
        Object cur = root;
        for (String k : dotted.split("\\.")) {
            if (!(cur instanceof Map)) {
                throw new IllegalStateException("정책 값이 없습니다: " + dotted);
            }
            cur = ((Map<String, Object>) cur).get(k);
        }
        if (cur == null) {
            throw new IllegalStateException("정책 값이 없습니다: " + dotted);
        }
        return cur;
    }

    public long num(String dotted) {
        return ((Number) get(dotted)).longValue();
    }

    public BigDecimal won(String dotted) {
        return BigDecimal.valueOf(num(dotted));
    }

    public String text(String dotted) {
        return String.valueOf(get(dotted));
    }

    @SuppressWarnings("unchecked")
    public List<String> list(String dotted) {
        return ((List<Object>) get(dotted)).stream().map(String::valueOf).toList();
    }
}

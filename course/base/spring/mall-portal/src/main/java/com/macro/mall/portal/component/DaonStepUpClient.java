package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * [교육용 추가 T22] 고위험 작업 앞 문자 본인 확인. 문자 사업자의 「인증 번호 확인 API」를 부른다(랩은 가짜 sms-stub).
 *
 * start(번호)          → 사업자가 여섯 자리 번호를 문자로 보낸다
 * verify(번호, 번호)   → 사업자에게 맞는지 묻는다
 *
 * 9-2 시작 상태: 사업자가 오류를 내면 「통과」로 처리한다(fail-open). 시간 제한도 정하지 않았다.
 */
@Component
public class DaonStepUpClient {
    private static final Logger LOG = LoggerFactory.getLogger(DaonStepUpClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${daon.sms.url:http://sms-stub:8000}")
    private String baseUrl;

    public void start(String phone) throws Exception {
        HttpResponse<String> res = post("/v1/verify/start", Map.of("to", phone));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("문자 사업자 응답 HTTP " + res.statusCode());
        }
    }

    public boolean verify(String phone, String code) {
        try {
            HttpResponse<String> res = post("/v1/verify/check", Map.of("to", phone, "code", code));
            if (res.statusCode() != 200) {
                throw new IllegalStateException("문자 사업자 응답 HTTP " + res.statusCode());
            }
            JsonNode body = JSON.readTree(res.body());
            return body.path("valid").asBoolean(false);
        } catch (Exception e) {
            // 장애 때 고객 불편 줄이기: 사업자가 응답하지 않으면 일단 통과시킨다
            LOG.warn("문자 사업자 확인 실패, 통과 처리: {}", e.getMessage());
            return true;
        }
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}

package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * [교육용 추가] 문자 보내기(랩의 가짜 문자 발송사 sms-stub, http://sms-stub:8000).
 *
 * 원본 주석이 「실제 환경에서는 SMS 서비스를 연동해야 함」이라고 적어 둔 자리의 연동 부품이다.
 * 가짜 발송사는 010-0000-xxxx만 받고, 실제로 보내지 않고 목록에 쌓기만 한다(stubs/README.md).
 * 원본 인증 번호 흐름(/sso/getAuthCode)은 처음에는 이 부품을 쓰지 않는다. 4-5에서 쓴다.
 */
@Component
public class DaonSmsSender {
    private static final Logger LOG = LoggerFactory.getLogger(DaonSmsSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${daon.sms.url:http://sms-stub:8000}")
    private String baseUrl;

    /** 보내고 true. 발송사가 거절하거나 닿지 않으면 로그만 남기고 false(번호·본문은 로그에 넣지 않는다) */
    public boolean send(String telephone, String text) {
        try {
            String body = JSON.writeValueAsString(Map.of("to", dashed(telephone), "text", text));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2) {
                LOG.warn("문자 발송사가 거절했습니다(HTTP {})", res.statusCode());
                return false;
            }
            return true;
        } catch (java.io.IOException e) {
            LOG.warn("문자 발송사에 닿지 않습니다: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 01000000101 → 010-0000-0101 (하이픈 없이 입력해도 발송사 형식으로 맞춘다) */
    static String dashed(String telephone) {
        String d = telephone == null ? "" : telephone.replaceAll("\\D", "");
        return d.length() == 11 ? d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7) : telephone;
    }
}

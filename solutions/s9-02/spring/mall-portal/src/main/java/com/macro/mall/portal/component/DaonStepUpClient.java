package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * [다온마켓 9-2 정답, T22] 고위험 작업 앞 문자 본인 확인. 확인할 수 없으면 통과가 아니라 「확인 불가」(fail-closed).
 *
 * - 연결 2초·응답 2초 시간 제한을 코드에 적는다(기본값은 제한이 없다).
 * - 사업자가 오류(5xx·4xx 아닌 이상한 답)·시간 초과·연결 실패면 DependencyUnavailableException. 컨트롤러가 503으로 답한다.
 * - 「번호가 틀림」(valid=false)과 「확인 불가」를 섞지 않는다. 사용자는 다시 시도할지, 운영자는 장애인지 공격인지 판단한다.
 * - SecureKitSwitch.on("fail-closed") 줄은 영상용 랩 스위치다(끄면 옛 동작: 오류면 통과).
 */
@Component
public class DaonStepUpClient {
    private static final Logger LOG = LoggerFactory.getLogger(DaonStepUpClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Value("${daon.sms.url:http://sms-stub:8000}")
    private String baseUrl;

    public void start(String phone) throws Exception {
        HttpResponse<String> res = post("/v1/verify/start", Map.of("to", phone));
        if (res.statusCode() / 100 != 2) {
            throw new DependencyUnavailableException("응답 " + res.statusCode());
        }
    }

    /** true = 번호가 맞음, false = 번호가 틀림. 확인할 수 없으면 DependencyUnavailableException */
    public boolean verify(String phone, String code) {
        try {
            HttpResponse<String> res = post("/v1/verify/check", Map.of("to", phone, "code", code));
            if (res.statusCode() != 200) {
                throw new DependencyUnavailableException("응답 " + res.statusCode());
            }
            JsonNode body = JSON.readTree(res.body());
            if (!body.has("valid")) {
                throw new DependencyUnavailableException("응답 형식이 다름");
            }
            return body.path("valid").asBoolean(false);
        } catch (DependencyUnavailableException e) {
            return failOpenOr(e);
        } catch (HttpTimeoutException e) {
            return failOpenOr(new DependencyUnavailableException("시간 초과"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failOpenOr(new DependencyUnavailableException("중단됨"));
        } catch (Exception e) {
            return failOpenOr(new DependencyUnavailableException("연결 실패(" + e.getClass().getSimpleName() + ")"));
        }
    }

    private boolean failOpenOr(DependencyUnavailableException e) {
        if (!SecureKitSwitch.on("fail-closed")) {
            LOG.warn("문자 사업자 확인 실패, 통과 처리(랩 스위치 fail-closed 꺼짐): {}", e.getMessage());
            return true;
        }
        throw e;
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** 의존 서비스(문자 사업자)에 물을 수 없음. 「인증 실패」가 아니라 「확인 불가」 */
    public static class DependencyUnavailableException extends RuntimeException {
        public DependencyUnavailableException(String reason) {
            super(reason);
        }
    }
}

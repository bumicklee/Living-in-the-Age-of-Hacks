package com.macro.mall.common.exception;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLSyntaxErrorException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 6-5: DB 예외가 응답에 SQL 문구·표 이름을 싣지 않고 일반 문구와 trace_id만 내보내는지(값은 가짜 문구). */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @SuppressWarnings("unchecked")
    void sqlExceptionTextIsNotInResponse() {
        MDC.put("trace_id", "unit-trace-1");
        try {
            ResponseEntity<Object> r = handler.handleDatabaseException(
                    new SQLSyntaxErrorException("You have an error in your SQL syntax near 'pms_product_secret_table'"));
            assertEquals(500, r.getStatusCode().value());
            Map<String, Object> body = (Map<String, Object>) r.getBody();
            String text = body.toString();
            assertFalse(text.contains("SQL syntax") || text.contains("pms_product"), "응답에 SQL 문구·표 이름이 있으면 안 됨");
            assertEquals("unit-trace-1", body.get("trace_id"));
            assertEquals("요청을 처리하지 못했습니다", body.get("message"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void dataAccessExceptionIsMaskedToo() {
        ResponseEntity<Object> r = handler.handleDatabaseException(new DataAccessResourceFailureException("jdbc:mysql://db:3306 연결 실패"));
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertTrue(r.getStatusCode().is5xxServerError());
        assertFalse(body.toString().contains("jdbc"), "응답에 접속 주소가 있으면 안 됨");
    }
}

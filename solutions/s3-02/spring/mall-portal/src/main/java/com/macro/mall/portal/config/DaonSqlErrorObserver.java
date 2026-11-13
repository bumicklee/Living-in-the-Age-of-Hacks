package com.macro.mall.portal.config;

import com.macro.mall.security.event.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLSyntaxErrorException;

/**
 * 다온마켓 3-2 ⑤ 탐지: 바인딩 뒤의 SQL 문법 오류는 「아직 조립하는 곳」 또는 「찾고 있는 사람」의 신호다.
 *
 * - 원본 공통 처리기(mall-common GlobalExceptionHandler)는 mall-security를 모르므로, 고객 앱에 관찰자를 하나 둔다.
 * - 응답은 바꾸지 않는다(null을 돌려 다음 처리기에 넘긴다). 오류 문구를 숨기는 일은 6-5에서 한다(F31).
 * - SQL 문장과 입력 값은 로그에 넣지 않는다.
 */
@Component
public class DaonSqlErrorObserver implements HandlerExceptionResolver, Ordered {

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (isSqlGrammarError(ex)) {
            SecurityEventLogger.denied("db.error.bad_sql_grammar", "3-2")
                    .target("path:" + request.getRequestURI())
                    .reason("SQL 문법 오류")
                    .request(request)
                    .log();
        }
        return null;
    }

    private static boolean isSqlGrammarError(Throwable e) {
        for (int i = 0; e != null && i < 10; i++, e = e.getCause()) {
            if (e instanceof BadSqlGrammarException || e instanceof SQLSyntaxErrorException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;   // 다른 처리기보다 먼저 보고, 처리는 넘긴다
    }
}

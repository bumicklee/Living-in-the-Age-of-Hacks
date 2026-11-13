package com.macro.mall.security.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * [다온마켓 8-3] 개인정보가 나가는 관리자 조회에 붙이는 감사 로그 표시(docs/audit-schema.yaml).
 *
 * <pre>
 * &#64;Audited(action = "customer.list", resource = "member")
 * public CommonResult&lt;List&lt;...&gt;&gt; list() { ... }
 * </pre>
 *
 * 붙인 메서드가 끝나면 AuditAspect가 AUDIT 로거로 한 줄(누가·무엇을·몇 건·왜·결과)을 남긴다.
 * 빠뜨린 곳은 이 어노테이션이 있는지로 찾는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    /** 감사 행동 이름(customer.list, order.detail …). docs/audit-schema.yaml의 actions 표와 같다 */
    String action();

    /** 대상 종류(member, order, return …). target은 「종류:ID」 또는 「종류:list?page=…&size=…」가 된다 */
    String resource();

    /**
     * [다온마켓 15장 시작 모습] 대상 ID를 경로 변수가 아니라 요청 본문(@RequestBody)의 칸에서 읽을 때 그 칸 이름(예: "orderId").
     * 비워 두면 예전과 같다(경로 변수 → 없으면 「종류:list?…」). 본문의 다른 칸(이름·주소 등)은 읽지 않는다.
     */
    String idField() default "";
}

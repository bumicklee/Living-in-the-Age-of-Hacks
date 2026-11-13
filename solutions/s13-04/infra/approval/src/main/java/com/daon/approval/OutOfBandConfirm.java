package com.daon.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/**
 * [13-4 정답] 다른 경로 확인. 고액 요청이 들어오면 6자리 확인 번호를 만들어 <b>메일(Mailpit)로만</b> 재무 담당에게 보낸다.
 *
 * - 번호는 SecureRandom, 저장은 SHA-256 해시만(원문은 DB·로그·응답 어디에도 없다). 5분, 한 번만, 5번 틀리면 끝.
 * - 승인 API 응답에 번호를 넣지 않는다(4-5 F13에서 배운 것: 응답에 실으면 승인 화면을 장악한 사람이 번호도 같이 얻는다).
 * - 「별도 경로」를 메일로 한 이유: 문자 단독(4-7)과 메시지가 엇갈리지 않게. 회사에서는 승인 앱 푸시·하드웨어 키를 쓴다.
 */
@Component
public class OutOfBandConfirm {
    private static final Logger LOG = LoggerFactory.getLogger("DAON-APPROVAL");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ApprovalStore store;
    private final PolicyConfig policy;

    public OutOfBandConfirm(ApprovalStore store, PolicyConfig policy) {
        this.store = store;
        this.policy = policy;
    }

    /** 번호를 만들어 해시만 저장하고 메일로 보낸다. 메일이 실패하면 false(요청은 대기로 남고, 다시 보내기는 새 요청으로) */
    public boolean send(String approvalId) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.jdbc().update("UPDATE daon_approval_request SET confirm_hash = ?, confirm_expires = NOW() + INTERVAL ? MINUTE, confirm_tries = 0 WHERE id = ?",
                sha256(approvalId + ":" + code), policy.num("dual.confirm_minutes"), approvalId);
        try {
            Smtp.send(policy.text("dual.confirm_mail_to"), "[다온 승인] 확인 번호 · 승인 요청 " + approvalId,
                    "고액 환불 승인 요청 " + approvalId + "\n\n확인 번호: " + code + "\n\n"
                            + policy.num("dual.confirm_minutes") + "분 안에 한 번만 쓸 수 있습니다. 요청한 적이 없거나 금액이 이상하면 승인하지 말고 취소하세요.\n"
                            + "(교육용 랩 · 127.0.0.1 · 값은 가짜)");
            return true;
        } catch (Exception e) {
            LOG.warn("[13-4] 확인 번호 메일을 보내지 못했습니다: {}", e.getMessage());
            return false;
        }
    }

    /** 맞으면 true. 시도 횟수를 먼저 올리고(맞혀 보기 막기), 만료·횟수·해시를 본다. 맞으면 해시를 지운다(한 번만) */
    public boolean check(String approvalId, String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            store.jdbc().update("UPDATE daon_approval_request SET confirm_tries = confirm_tries + 1 WHERE id = ?", approvalId);
            return false;
        }
        store.jdbc().update("UPDATE daon_approval_request SET confirm_tries = confirm_tries + 1 WHERE id = ?", approvalId);
        Map<String, Object> row = store.jdbc().queryForMap(
                "SELECT confirm_hash, confirm_tries, (confirm_expires IS NOT NULL AND confirm_expires > NOW()) AS live FROM daon_approval_request WHERE id = ?",
                approvalId);
        Object hash = row.get("confirm_hash");
        boolean live = row.get("live") instanceof Number n && n.intValue() == 1;
        int tries = ((Number) row.get("confirm_tries")).intValue();
        if (hash == null || !live || tries > policy.num("dual.confirm_max_tries")) {
            return false;
        }
        boolean ok = MessageDigest.isEqual(String.valueOf(hash).getBytes(StandardCharsets.US_ASCII),
                sha256(approvalId + ":" + code).getBytes(StandardCharsets.US_ASCII));
        if (ok) {
            store.jdbc().update("UPDATE daon_approval_request SET confirm_hash = NULL WHERE id = ?", approvalId);
        }
        return ok;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

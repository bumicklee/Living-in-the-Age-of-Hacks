package com.macro.mall.security.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.openbao.OpenBaoClient;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [다온마켓 5-3 정답] 봉투 암호화로 개인정보 칸을 잠근다.
 *
 * <pre>
 * 칸에 들어가는 값:  enc:1:&lt;데이터 키 번호&gt;:&lt;논스(12바이트)+암호문+태그&gt;   (base64url)
 * 데이터 키:         AES-256. 평문은 앱 메모리에만(10분 캐시), DB에는 OpenBao가 감싼 값만(daon_data_key 표)
 * 감싸기·풀기:       OpenBao transit 키 daon-pii (datakey/plaintext · decrypt). 풀 때마다 OpenBao 감사 로그에 남는다
 * </pre>
 *
 * - 같은 평문도 논스가 달라 매번 다른 암호문이 된다 → 암호문끼리 비교·검색이 안 된다(5-4의 검색용 해시가 푼다).
 * - 읽을 때 {@code enc:}로 시작하지 않으면 평문 그대로 돌려준다(옛 행이 남아 있어도 앱이 멈추지 않게. 재암호화는 PiiReencrypt).
 * - 풀기에 실패하면(값이 바뀌었거나 키가 맞지 않음) crypto.decrypt.failed(WARN)를 남기고 null을 돌려준다. 값은 로그에 넣지 않는다.
 * - SecureKitSwitch.on("pii-encrypt") 줄은 영상용 랩 스위치다. 손으로 고칠 때는 쓰지 않는다. 끄면 평문으로 저장한다.
 */
@Component
public class FieldCrypto {
    private static final Logger LOG = LoggerFactory.getLogger(FieldCrypto.class);
    private static final String PREFIX = "enc:1:";
    private static final long KEY_CACHE_MILLIS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile FieldCrypto instance;

    @Autowired
    private OpenBaoClient bao;
    @Autowired
    private JdbcTemplate jdbc;

    private final Map<Integer, CachedKey> keys = new ConcurrentHashMap<>();
    private volatile boolean tableReady;

    private record CachedKey(byte[] key, long expiresAt) {
    }

    @PostConstruct
    void register() {
        instance = this;
    }

    /** MyBatis 타입 핸들러는 스프링이 만들지 않으므로 이 정적 통로로 쓴다. 아직 준비 전이면 null */
    public static FieldCrypto instance() {
        return instance;
    }

    public static boolean enabled() {
        return SecureKitSwitch.on("pii-encrypt");
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** 저장 전. 스위치가 꺼져 있거나 비어 있거나 이미 잠겨 있으면 그대로 */
    public String encrypt(String plain) {
        if (!enabled() || plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        int keyId = currentKeyId();
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey(keyId), "AES"), new GCMParameterSpec(128, nonce));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, all, 0, nonce.length);
            System.arraycopy(ct, 0, all, nonce.length, ct.length);
            return PREFIX + keyId + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(all);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("개인정보 칸을 잠그지 못했습니다", e);
        }
    }

    /** 읽은 뒤. enc:로 시작하지 않으면 평문(옛 행) 그대로. 풀기에 실패하면 이벤트를 남기고 null */
    public String decrypt(String stored, String where) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            int keyId = Integer.parseInt(parts[0]);
            byte[] all = Base64.getUrlDecoder().decode(parts[1]);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey(keyId), "AES"), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SecurityEventLogger.denied("crypto.decrypt.failed", "5-3")
                    .target(where == null ? "column:unknown" : where)
                    .reason("개인정보 칸 복호화 실패(값이 바뀌었거나 키가 맞지 않음)")
                    .log();
            return null;
        }
    }

    // ---- 데이터 키 ----

    private int currentKeyId() {
        ensureTable();
        List<Integer> ids = jdbc.queryForList("SELECT id FROM daon_data_key ORDER BY id DESC LIMIT 1", Integer.class);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        synchronized (this) {
            ids = jdbc.queryForList("SELECT id FROM daon_data_key ORDER BY id DESC LIMIT 1", Integer.class);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
            JsonNode dk = bao.write("transit/datakey/plaintext/daon-pii", Map.of());
            String wrapped = dk.path("ciphertext").asText();
            byte[] plain = Base64.getDecoder().decode(dk.path("plaintext").asText());
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("INSERT INTO daon_data_key (wrapped) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, wrapped);
                return ps;
            }, kh);
            int id = kh.getKey().intValue();
            keys.put(id, new CachedKey(plain, System.currentTimeMillis() + KEY_CACHE_MILLIS));
            LOG.info("[A4] 개인정보용 데이터 키 {}번을 만들었습니다(감싼 값만 DB에 저장)", id);
            return id;
        }
    }

    private byte[] dataKey(int keyId) {
        CachedKey cached = keys.get(keyId);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.key();
        }
        ensureTable();
        String wrapped = jdbc.queryForObject("SELECT wrapped FROM daon_data_key WHERE id = ?", String.class, keyId);
        JsonNode res = bao.write("transit/decrypt/daon-pii", Map.of("ciphertext", wrapped));
        byte[] plain = Base64.getDecoder().decode(res.path("plaintext").asText());
        keys.put(keyId, new CachedKey(plain, System.currentTimeMillis() + KEY_CACHE_MILLIS));
        return plain;
    }

    private void ensureTable() {
        if (!tableReady) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS daon_data_key ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, wrapped VARCHAR(512) NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP) COMMENT='개인정보 데이터 키(OpenBao가 감싼 값만)'");
            tableReady = true;
        }
    }
}

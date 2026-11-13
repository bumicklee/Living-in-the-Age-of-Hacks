package com.macro.mall.security.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * [다온마켓 8-6] 감사 로그 해시 체인.
 *
 * 줄마다 seq(일련번호), prev(앞 줄의 hash), hash(이 줄 내용 + prev의 SHA-256)를 넣는다.
 * - 해시 입력은 hash 칸을 뺀 줄을 「키 이름 순으로 정렬한 JSON」(공백 없음)으로 쓴 글자다. 같은 내용이면 같은 해시가 나온다.
 *   make audit-verify(파이썬)는 json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)로 같은 글자를 만든다.
 * - 중간 줄을 지우거나 바꾸면 다음 줄의 prev가 맞지 않는다(체인이 끊김).
 * - 끝 줄을 지우면 체인은 멀쩡하다. 그래서 마지막 seq·hash(머리 해시)를 앱이 지울 수 없는 곳(Loki 사본, 8-4)과 맞춰 본다.
 * - 체인은 「지우면 들키게」 할 뿐이다. 「지우지 못하게」는 별도 저장소의 잠금이 한다(14-2).
 * 앱이 다시 떠도 파일의 마지막 줄에서 seq·hash를 이어 받는다. 인스턴스가 둘이면 체인이 두 갈래가 된다(랩은 하나).
 */
public final class AuditChain {
    public static final String GENESIS = "0".repeat(64);
    private static final ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private long seq;
    private String prev = GENESIS;

    /** 파일의 마지막 체인 줄에서 이어 받는다(없거나 체인 전 줄이면 처음부터) */
    public synchronized void resumeFrom(Path file) {
        try {
            String last = lastLine(file);
            if (last == null) {
                return;
            }
            JsonNode n = JSON.readTree(last);
            if (n.hasNonNull("seq") && n.hasNonNull("hash")) {
                seq = n.get("seq").asLong();
                prev = n.get("hash").asText();
            }
        } catch (IOException | RuntimeException ignored) {
            // 읽지 못하면 처음부터(make audit-verify가 「체인 시작」을 알려 준다)
        }
    }

    /** seq·prev·hash를 붙인 줄(정렬된 JSON 글자)을 돌려준다 */
    public synchronized String link(Map<String, Object> entry) {
        TreeMap<String, Object> m = new TreeMap<>(entry);
        m.remove("hash");
        m.put("seq", ++seq);
        m.put("prev", prev);
        String hash = sha256(canonical(m));
        m.put("hash", hash);
        prev = hash;
        return canonical(m);
    }

    public static String canonical(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(m));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lastLine(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return null;
        }
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long pos = f.length() - 1;
            while (pos >= 0) {                       // 끝의 줄바꿈 건너뛰기
                f.seek(pos);
                int c = f.read();
                if (c != '\n' && c != '\r') {
                    break;
                }
                pos--;
            }
            long end = pos;
            while (pos >= 0) {
                f.seek(pos);
                if (f.read() == '\n') {
                    break;
                }
                pos--;
            }
            byte[] buf = new byte[(int) (end - pos)];
            f.seek(pos + 1);
            f.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }
}
